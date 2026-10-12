package no.digdir.forsystem.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.billing.Eksportfil;
import no.digdir.forsystem.billing.EksportfilRepository;
import no.digdir.forsystem.billing.Faktura;
import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.Fakturalinje;
import no.digdir.forsystem.billing.Lg04Avstemming;
import no.digdir.forsystem.billing.Lg04AvstemmingRepository;
import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.PdfEksport.FakturaPdf;
import no.digdir.forsystem.export.lg04.Lg04Leser;
import no.digdir.forsystem.export.lg04.Lg04Ordre;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates export of an approved run (docs/04 Phase 4): builds the LG04 file, per-invoice PDF zip,
 * and CSV/XLSX of the lines; archives each via {@link FileArchive} with a sha256 {@code eksportfil}
 * row; then moves the run to EKSPORTERT. Export is allowed only from GODKJENT, and only after the LG04
 * file has been read back and reconciled against the run (specs/002 D3) — the reconciliation is stored
 * as {@code lg04_avstemming}.
 *
 * <p>Default LG04 behaviour is one order per invoice line (the OQ-1-safe interpretation); the
 * alternate "multiple amount lines per order" mode waits on OQ-1.
 */
@Service
public class EksportService {

    private static final DateTimeFormatter BATCH = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FakturakjoringService kjoringer;
    private final ProduktRepository produkter;
    private final Lg04Eksport lg04;
    private final PdfEksport pdf;
    private final RegnearkEksport regneark;
    private final FileArchive arkiv;
    private final EksportfilRepository eksportfiler;
    private final AuditService audit;
    private final BrukerContext brukerContext;
    private final EksportKonfig konfig;
    private final Lg04AvstemmingRepository avstemminger;
    private final FakturagrunnlagRegneark grunnlag;
    private final Lg04Leser leser = new Lg04Leser();

    EksportService(FakturakjoringService kjoringer, ProduktRepository produkter, Lg04Eksport lg04,
                   PdfEksport pdf, RegnearkEksport regneark, FileArchive arkiv,
                   EksportfilRepository eksportfiler, AuditService audit, BrukerContext brukerContext,
                   EksportKonfig konfig, Lg04AvstemmingRepository avstemminger,
                   FakturagrunnlagRegneark grunnlag) {
        this.kjoringer = kjoringer;
        this.produkter = produkter;
        this.lg04 = lg04;
        this.pdf = pdf;
        this.regneark = regneark;
        this.arkiv = arkiv;
        this.eksportfiler = eksportfiler;
        this.audit = audit;
        this.brukerContext = brukerContext;
        this.konfig = konfig;
        this.avstemminger = avstemminger;
        this.grunnlag = grunnlag;
    }

    /**
     * Export an approved run (D3): build the LG04, read it back and reconcile it against the run, and
     * only then archive anything. A mismatch throws {@link Lg04Avvik} before the first file is written,
     * so the run stays GODKJENT with no export files; the caller logs it via {@link #loggStoppetEksport}.
     */
    @Transactional
    public void eksporter(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        if (!"GODKJENT".equals(kj.status())) {
            throw new Regelbrudd("Bare en godkjent kjøring kan eksporteres (status er " + kj.status() + ")");
        }
        Map<Long, Produkt> produktById = produktById();
        List<Faktura> fakturaer = kjoringer.fakturaer(kjoringId);
        String periode = kj.periode().toString();

        // 1–4: build, read back, compare — nothing is archived until the file is proven to match.
        List<Lg04Ordre> ordrer = ordrerFor(kj, fakturaer, produktById);
        byte[] lg04Bytes = lg04.bygg(ordrer);
        Lg04Leser.Lg04Innhold fil = leser.les(lg04Bytes);
        long sumOreKjoring = sumOre(fakturaer);
        if (!fil.gyldig() || fil.antallOrdrer() != ordrer.size() || fil.sumOre() != sumOreKjoring) {
            throw new Lg04Avvik(periode, fil.antallOrdrer(), fil.sumOre(), ordrer.size(), sumOreKjoring, fil.feil());
        }
        String sha256 = sha256(lg04Bytes);

        byte[] pdfZip = pdf.byggZip(byggPdfer(kj, fakturaer, produktById));
        FakturagrunnlagRegneark.Tabell tabell = grunnlag.bygg(kj);
        byte[] csvBytes = regneark.csv(tabell.kolonner(), tabell.rader());
        byte[] xlsxBytes = regneark.xlsx("fakturagrunnlag", tabell.kolonner(), tabell.rader());

        String mappe = "kjoring-" + kjoringId + "/";
        Eksportfil lg04Fil = arkiver(kjoringId, "LG04", mappe + "lg04-" + periode + ".txt", lg04Bytes);
        if (!sha256.equals(lg04Fil.sha256())) {
            // The archive stored something other than the bytes that were checked.
            throw new Lg04Avvik(periode, fil.antallOrdrer(), fil.sumOre(), ordrer.size(), sumOreKjoring,
                    List.of("Arkivert LG04 har en annen sjekksum enn filen som ble avstemt"));
        }
        arkiver(kjoringId, "PDF", mappe + "pdf-" + periode + ".zip", pdfZip);
        arkiver(kjoringId, "CSV", mappe + "fakturagrunnlag-" + periode + ".csv", csvBytes);
        arkiver(kjoringId, "XLSX", mappe + "fakturagrunnlag-" + periode + ".xlsx", xlsxBytes);

        avstemminger.save(new Lg04Avstemming(null, kjoringId, lg04Fil.id(), fil.antallOrdrer(), fil.sumOre(),
                ordrer.size(), sumOreKjoring, fakturaer.size(), sha256, brukerContext.naavaerendeBruker(),
                OffsetDateTime.now()));
        kjoringer.settEksportert(kjoringId);
        audit.logg(Handling.EKSPORTERTE, "FAKTURAKJORING", kjoringId,
                Map.of("periode", periode, "antallFakturaer", fakturaer.size(), "antallOrdrer", ordrer.size(),
                        "sumOre", sumOreKjoring, "lg04Sha256", sha256));
    }

    /** A spreadsheet download: file name, media type and content. */
    public record Nedlasting(String filnavn, String mediatype, byte[] innhold) {
    }

    /**
     * The fakturagrunnlag as CSV or XLSX for any run that is not discarded — also before approval, so
     * Økonomi can sample it in a spreadsheet (specs/002 US3). Changes nothing, records no export file, and
     * logs the download. The file name carries the run's status so a draft is never taken for the
     * approved basis.
     */
    @Transactional   // not read-only: it writes the LASTET_NED audit row
    public Nedlasting regneark(Long kjoringId, String format) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        if ("FORKASTET".equals(kj.status())) {
            throw new Regelbrudd("Forkastede kjøringer kan ikke lastes ned som fakturagrunnlag");
        }
        FakturagrunnlagRegneark.Tabell tabell = grunnlag.bygg(kj);
        String navn = "fakturagrunnlag-" + kj.periode().toString().substring(0, 7) + "-" + kj.status();
        Nedlasting n = switch (format) {
            case "csv" -> new Nedlasting(navn + ".csv", "text/csv",
                    regneark.csv(tabell.kolonner(), tabell.rader()));
            case "xlsx" -> new Nedlasting(navn + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    regneark.xlsx("fakturagrunnlag", tabell.kolonner(), tabell.rader()));
            default -> throw new Regelbrudd("Ukjent format: " + format + " (csv eller xlsx)");
        };
        audit.logg(Handling.LASTET_NED, "FAKTURAKJORING", kjoringId,
                Map.of("periode", kj.periode().toString(), "format", format.toUpperCase(), "status", kj.status()));
        return n;
    }

    /**
     * Record a stopped export (D7). Deliberately not transactional: it runs after {@link #eksporter}
     * rolled back, so the audit row is not lost with the rest.
     */
    public void loggStoppetEksport(Long kjoringId, Lg04Avvik avvik) {
        audit.logg(Handling.EKSPORT_STOPPET, "FAKTURAKJORING", kjoringId, avvik.detaljer());
    }

    /** How many LG04 orders the export will write for this run, from the export's own assembly (D5, OQ-1). */
    @Transactional(readOnly = true)
    public int forventetAntallOrdrer(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        return ordrerFor(kj, kjoringer.fakturaer(kjoringId), produktById()).size();
    }

    /**
     * The LG04 reconciliation to show on the run page (D6): the stored one, or — for a run exported
     * before reconciliation existed — one computed now from the archived file, not stored. Empty for runs
     * that are not exported.
     */
    @Transactional(readOnly = true)
    public Optional<Lg04AvstemmingVisning> avstemmingFor(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        if (!"EKSPORTERT".equals(kj.status())) {
            return Optional.empty();
        }
        return Optional.of(avstemminger.findByKjoringId(kjoringId)
                .map(Lg04AvstemmingVisning::lagret)
                .orElseGet(() -> fraArkiv(kj)));
    }

    /** Re-read and re-hash the archived LG04 of an exported run (FR-007). Read-only. */
    @Transactional(readOnly = true)
    public Lg04AvstemmingVisning kontrollerArkiv(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        if (!"EKSPORTERT".equals(kj.status())) {
            throw new Regelbrudd("Bare en eksportert kjøring har en LG04-fil å kontrollere");
        }
        return fraArkiv(kj);
    }

    private Lg04AvstemmingVisning fraArkiv(Fakturakjoring kj) {
        Optional<Eksportfil> lg04Fil = kjoringer.eksportfiler(kj.id()).stream()
                .filter(f -> "LG04".equals(f.type())).findFirst();
        if (lg04Fil.isEmpty()) {
            return Lg04AvstemmingVisning.feil("Kjøringen har ingen arkivert LG04-fil");
        }
        byte[] bytes;
        try {
            bytes = arkiv.hent(lg04Fil.get().blobUrl());
        } catch (RuntimeException e) {
            return Lg04AvstemmingVisning.feil("Den arkiverte LG04-filen kunne ikke leses: " + e.getMessage());
        }
        String sha256 = sha256(bytes);
        if (lg04Fil.get().sha256() != null && !lg04Fil.get().sha256().equals(sha256)) {
            return Lg04AvstemmingVisning.feil("Den arkiverte LG04-filen har endret seg siden eksport (sjekksummen stemmer ikke)");
        }
        List<Faktura> fakturaer = kjoringer.fakturaer(kj.id());
        Lg04Leser.Lg04Innhold fil = leser.les(bytes);
        if (!fil.gyldig()) {
            return Lg04AvstemmingVisning.feil("Den arkiverte LG04-filen er ikke gyldig: " + String.join("; ", fil.feil()));
        }
        return Lg04AvstemmingVisning.fraArkiv(fil.antallOrdrer(), fil.sumOre(),
                ordrerFor(kj, fakturaer, produktById()).size(), sumOre(fakturaer), fakturaer.size(), sha256);
    }

    /** The run's order assembly — the single place that decides how lines become LG04 orders (D5). */
    private List<Lg04Ordre> ordrerFor(Fakturakjoring kj, List<Faktura> fakturaer, Map<Long, Produkt> produktById) {
        return byggOrdrer(fakturaer, produktById, kj.generertAt().format(BATCH));
    }

    /** The run's amount in øre, with the export's own rounding: Σ over lines, not over invoices. */
    private long sumOre(List<Faktura> fakturaer) {
        long sum = 0;
        for (Faktura f : fakturaer) {
            for (Fakturalinje l : kjoringer.linjer(f.id())) {
                sum = Math.addExact(sum, Long.parseLong(ore(l.belop())));
            }
        }
        return sum;
    }

    private Map<Long, Produkt> produktById() {
        Map<Long, Produkt> produktById = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktById.put(p.id(), p));
        return produktById;
    }

    private static String sha256(byte[] innhold) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(innhold));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 mangler i JVM-en", e);
        }
    }

    private List<Lg04Ordre> byggOrdrer(List<Faktura> fakturaer, Map<Long, Produkt> produktById, String batchId) {
        List<Lg04Ordre> ordrer = new ArrayList<>();
        int serie = 1;
        for (Faktura f : fakturaer) {
            for (Fakturalinje l : kjoringer.linjer(f.id())) {
                Produkt p = produktById.get(l.produktId());
                ordrer.add(new Lg04Ordre(
                        p.konto(),
                        p.artikkelId() == null ? null : String.valueOf(p.artikkelId()),
                        p.dim1(), p.dim2(), p.dim4(),
                        f.kundenummer(), l.fakturareferanse(), l.bestillingsnummer(), l.beskrivelse(),
                        ore(l.belop()), serie, url(f), konfig.ansvarlig1(), konfig.ansvarlig2(),
                        batchId, serie));
                serie++;
            }
        }
        return ordrer;
    }

    private List<FakturaPdf> byggPdfer(Fakturakjoring kj, List<Faktura> fakturaer, Map<Long, Produkt> produktById) {
        Map<Long, String> kundenavn = kjoringer.kundenavn(fakturaer);
        List<FakturaPdf> pdfer = new ArrayList<>();
        for (Faktura f : fakturaer) {
            String filnavn = f.kundenummer() + "-" + f.fakturaUuid() + ".pdf";
            pdfer.add(new FakturaPdf(filnavn, pdfHtml(kj, f, kundenavn.get(f.id()), produktById)));
        }
        return pdfer;
    }

    private Eksportfil arkiver(Long kjoringId, String type, String sti, byte[] innhold) {
        FileArchive.Arkivert a = arkiv.lagre(sti, innhold);
        String filnavn = sti.substring(sti.lastIndexOf('/') + 1);
        return eksportfiler.save(new Eksportfil(null, kjoringId, type, filnavn, a.blobUrl(), a.sha256(),
                brukerContext.naavaerendeBruker(), OffsetDateTime.now()));
    }

    private String url(Faktura f) {
        return konfig.fakturaUrlBase() + "/" + f.kundenummer() + "-" + f.fakturaUuid() + ".pdf";
    }

    private String pdfHtml(Fakturakjoring kj, Faktura f, String kundenavn, Map<Long, Produkt> produktById) {
        StringBuilder rader = new StringBuilder();
        for (Fakturalinje l : kjoringer.linjer(f.id())) {
            Produkt p = produktById.get(l.produktId());
            rader.append("<tr><td>").append(esc(p.navn())).append("</td><td>").append(esc(l.beskrivelse()))
                    .append("</td><td class='h'>").append(tekst(l.antall()))
                    .append("</td><td class='h'>").append(tekst(l.enhetspris()))
                    .append("</td><td class='h'>").append(l.belop().toPlainString()).append("</td></tr>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml"><head><style>
                body { font-family: sans-serif; font-size: 11pt; }
                h1 { font-size: 16pt; } table { width: 100%%; border-collapse: collapse; }
                th, td { border-bottom: 1px solid #ccc; text-align: left; padding: 4px; }
                .h { text-align: right; }
                </style></head><body>
                <h1>Fakturagrunnlag</h1>
                <p>Kunde: %s<br/>Periode: %s<br/>Kundenummer: %s<br/>Fakturamottaker (orgnr): %s<br/>Ordre: %d</p>
                <table><thead><tr><th>Produkt</th><th>Beskrivelse</th><th class='h'>Antall</th>
                <th class='h'>Enhetspris</th><th class='h'>Beløp</th></tr></thead>
                <tbody>%s</tbody></table>
                <p><strong>Sum: %s</strong></p>
                </body></html>
                """.formatted(esc(kundenavn), esc(kj.periode().toString()), esc(f.kundenummer()),
                esc(f.fakturamottakerOrgnr()), f.ordreNr(), rader, f.sumBelop().toPlainString());
    }

    private static String ore(BigDecimal belop) {
        return belop.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
    }

    private static String tekst(BigDecimal b) {
        return b == null ? "" : b.toPlainString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
