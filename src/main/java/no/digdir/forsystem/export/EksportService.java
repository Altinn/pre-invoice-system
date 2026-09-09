package no.digdir.forsystem.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.billing.Eksportfil;
import no.digdir.forsystem.billing.EksportfilRepository;
import no.digdir.forsystem.billing.Faktura;
import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.Fakturalinje;
import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.PdfEksport.FakturaPdf;
import no.digdir.forsystem.export.lg04.Lg04Ordre;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates export of an approved run (docs/04 Phase 4): builds the LG04 file, per-invoice PDF zip,
 * and CSV/XLSX of the lines; archives each via {@link FileArchive} with a sha256 {@code eksportfil}
 * row; then moves the run to EKSPORTERT. Export is allowed only from GODKJENT.
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

    EksportService(FakturakjoringService kjoringer, ProduktRepository produkter, Lg04Eksport lg04,
                   PdfEksport pdf, RegnearkEksport regneark, FileArchive arkiv,
                   EksportfilRepository eksportfiler, AuditService audit, BrukerContext brukerContext,
                   EksportKonfig konfig) {
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
    }

    @Transactional
    public void eksporter(Long kjoringId) {
        Fakturakjoring kj = kjoringer.hent(kjoringId);
        if (!"GODKJENT".equals(kj.status())) {
            throw new Regelbrudd("Bare en godkjent kjøring kan eksporteres (status er " + kj.status() + ")");
        }
        Map<Long, Produkt> produktById = new LinkedHashMap<>();
        produkter.findAllByOrderByKode().forEach(p -> produktById.put(p.id(), p));

        List<Faktura> fakturaer = kjoringer.fakturaer(kjoringId);
        String batchId = kj.generertAt().format(BATCH);
        String periode = kj.periode().toString();

        byte[] lg04Bytes = lg04.bygg(byggOrdrer(fakturaer, produktById, batchId));
        byte[] pdfZip = pdf.byggZip(byggPdfer(kj, fakturaer, produktById));
        List<String> hode = List.of("periode", "ordre", "kundenummer", "produkt", "beskrivelse",
                "antall", "enhetspris", "belop");
        List<List<String>> rader = byggRader(fakturaer, produktById, periode);
        byte[] csvBytes = regneark.csv(hode, rader);
        byte[] xlsxBytes = regneark.xlsx("fakturagrunnlag", hode, rader);

        String mappe = "kjoring-" + kjoringId + "/";
        arkiver(kjoringId, "LG04", mappe + "lg04-" + periode + ".txt", lg04Bytes);
        arkiver(kjoringId, "PDF", mappe + "pdf-" + periode + ".zip", pdfZip);
        arkiver(kjoringId, "CSV", mappe + "fakturagrunnlag-" + periode + ".csv", csvBytes);
        arkiver(kjoringId, "XLSX", mappe + "fakturagrunnlag-" + periode + ".xlsx", xlsxBytes);

        kjoringer.settEksportert(kjoringId);
        audit.logg(Handling.EKSPORTERTE, "FAKTURAKJORING", kjoringId,
                Map.of("periode", periode, "antallFakturaer", fakturaer.size()));
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
        List<FakturaPdf> pdfer = new ArrayList<>();
        for (Faktura f : fakturaer) {
            String filnavn = f.kundenummer() + "-" + f.fakturaUuid() + ".pdf";
            pdfer.add(new FakturaPdf(filnavn, pdfHtml(kj, f, produktById)));
        }
        return pdfer;
    }

    private List<List<String>> byggRader(List<Faktura> fakturaer, Map<Long, Produkt> produktById, String periode) {
        List<List<String>> rader = new ArrayList<>();
        for (Faktura f : fakturaer) {
            for (Fakturalinje l : kjoringer.linjer(f.id())) {
                Produkt p = produktById.get(l.produktId());
                rader.add(List.of(periode, String.valueOf(f.ordreNr()), f.kundenummer(), p.kode(),
                        l.beskrivelse(), tekst(l.antall()), tekst(l.enhetspris()), l.belop().toPlainString()));
            }
        }
        return rader;
    }

    private void arkiver(Long kjoringId, String type, String sti, byte[] innhold) {
        FileArchive.Arkivert a = arkiv.lagre(sti, innhold);
        String filnavn = sti.substring(sti.lastIndexOf('/') + 1);
        eksportfiler.save(new Eksportfil(null, kjoringId, type, filnavn, a.blobUrl(), a.sha256(),
                brukerContext.naavaerendeBruker(), OffsetDateTime.now()));
    }

    private String url(Faktura f) {
        return konfig.fakturaUrlBase() + "/" + f.kundenummer() + "-" + f.fakturaUuid() + ".pdf";
    }

    private String pdfHtml(Fakturakjoring kj, Faktura f, Map<Long, Produkt> produktById) {
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
                <p>Periode: %s<br/>Kundenummer: %s<br/>Fakturamottaker (orgnr): %s<br/>Ordre: %d</p>
                <table><thead><tr><th>Produkt</th><th>Beskrivelse</th><th class='h'>Antall</th>
                <th class='h'>Enhetspris</th><th class='h'>Beløp</th></tr></thead>
                <tbody>%s</tbody></table>
                <p><strong>Sum: %s</strong></p>
                </body></html>
                """.formatted(esc(kj.periode().toString()), esc(f.kundenummer()),
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
