package no.digdir.forsystem.usage;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usage import: the shared validation pipeline (identical regardless of source — docs/06 §1) that
 * turns raw rows into a {@link Forhaandsvisning}, and the commit that persists an import applying
 * the re-import replacement rule (docs/03 §4). Import is all-or-nothing: any reject or blocker
 * prevents the commit, so a rejected file leaves zero rows. The datavarehus source
 * ({@link DwhImportService}) feeds the same {@link #vurder} pipeline and {@link #importer} commit.
 */
@Service
public class UsageImportService {

    private final UsageDataSource usageDataSource;
    private final ProduktRepository produkter;
    private final BruksdataImportRepository importer;
    private final BruksdataRepository bruksdata;
    private final KjoringStatusPort kjoringStatus;
    private final AuditService audit;
    private final BrukerContext brukerContext;
    private final FileArchive arkiv;
    private final DwhEgenskaper dwh;

    UsageImportService(UsageDataSource usageDataSource, ProduktRepository produkter,
                       BruksdataImportRepository importer, BruksdataRepository bruksdata,
                       KjoringStatusPort kjoringStatus, AuditService audit, BrukerContext brukerContext,
                       FileArchive arkiv, DwhEgenskaper dwh) {
        this.usageDataSource = usageDataSource;
        this.produkter = produkter;
        this.bruksdata = bruksdata;
        this.importer = importer;
        this.kjoringStatus = kjoringStatus;
        this.audit = audit;
        this.brukerContext = brukerContext;
        this.arkiv = arkiv;
        this.dwh = dwh;
    }

    @Transactional(readOnly = true)
    public Forhaandsvisning forhaandsvis(Periode periode, String filnavn, InputStream innhold)
            throws IOException {
        return vurder(periode, filnavn, Kilde.CSV, usageDataSource.lesRader(periode, innhold),
                List.of(), List.of(), null, null);
    }

    /**
     * The shared pipeline: validate raw rows, add blockers and month-over-month warnings. Rejects found
     * earlier by a source adapter ({@code forhaandsAvviste}) and source-specific warnings are merged in.
     */
    Forhaandsvisning vurder(Periode periode, String filnavn, Kilde kilde, List<RaaBruksrad> raarader,
                            List<AvvistRad> forhaandsAvviste, List<String> kildeAdvarsler,
                            Raadata raadata, Long stagetImportId) {
        Map<String, Long> kodeTilId = new LinkedHashMap<>();
        for (Produkt p : produkter.findAllByOrderByKode()) {
            kodeTilId.put(p.kode(), p.id());
        }

        List<Bruksdata> gyldige = new ArrayList<>();
        List<AvvistRad> avviste = new ArrayList<>(forhaandsAvviste);
        Set<String> setteNokler = new HashSet<>();

        for (RaaBruksrad rad : raarader) {
            valider(rad, periode, kodeTilId, setteNokler, gyldige, avviste);
        }

        List<String> blokkeringer = new ArrayList<>();
        if (kjoringStatus.finnesIkkeForkastetKjoring(periode.førsteDag())) {
            blokkeringer.add("Det finnes allerede en fakturakjøring for " + periode
                    + " som ikke er forkastet. Forkast den før ny import.");
        }

        List<ProduktSum> summer = summer(gyldige, kodeTilId);
        List<String> advarsler = new ArrayList<>(kildeAdvarsler);
        advarsler.addAll(avvikMotForrigeMaaned(periode, summer, kodeTilId));

        return new Forhaandsvisning(periode, filnavn, kilde, gyldige, avviste, summer, blokkeringer,
                advarsler, raadata, stagetImportId);
    }

    private void valider(RaaBruksrad rad, Periode periode, Map<String, Long> kodeTilId,
                         Set<String> setteNokler, List<Bruksdata> gyldige, List<AvvistRad> avviste) {
        LocalDate radPeriode = parseDato(rad.periode());
        if (radPeriode == null || !radPeriode.equals(periode.førsteDag())) {
            avviste.add(new AvvistRad(rad.referanse(), "feil periode (forventet " + periode.førsteDag() + ")"));
            return;
        }
        if (rad.organisasjonsnummer() == null || !rad.organisasjonsnummer().matches("^[0-9]{9}$")) {
            avviste.add(new AvvistRad(rad.referanse(), "ugyldig organisasjonsnummer: " + rad.organisasjonsnummer()));
            return;
        }
        Long produktId = rad.produktkode() == null ? null : kodeTilId.get(rad.produktkode());
        if (produktId == null) {
            avviste.add(new AvvistRad(rad.referanse(), "ukjent produktkode: " + rad.produktkode()));
            return;
        }
        Bruksdatatype type = parseType(rad.type());
        if (type == null) {
            avviste.add(new AvvistRad(rad.referanse(), "ugyldig type: " + rad.type()));
            return;
        }

        BigDecimal antall = null;
        BigDecimal belop = null;
        try {
            if (type.erVolum()) {
                antall = krevIkkeNegativ(rad.antall(), "antall");
            } else {
                belop = krevIkkeNegativ(rad.belop(), "beløp");
            }
        } catch (UgyldigVerdi e) {
            avviste.add(new AvvistRad(rad.referanse(), e.getMessage()));
            return;
        }

        String nokkel = periode + "|" + rad.organisasjonsnummer() + "|" + produktId + "|" + type;
        if (!setteNokler.add(nokkel)) {
            avviste.add(new AvvistRad(rad.referanse(), "duplikat (samme periode/orgnr/produkt/type)"));
            return;
        }

        gyldige.add(new Bruksdata(null, null, periode.førsteDag(),
                rad.organisasjonsnummer(), produktId, type.name(), antall, belop));
    }

    @Transactional
    public BruksdataImport importer(Forhaandsvisning fv) {
        if (!fv.kanImporteres()) {
            throw new Regelbrudd("Importen kan ikke fullføres: den har avviste rader eller er blokkert");
        }
        LocalDate periode = fv.periode().førsteDag();
        String bruker = brukerContext.naavaerendeBruker();

        // Re-import replacement (docs/03 §4): drop the period's existing rows and mark old imports AVVIST.
        // A staged import being confirmed is the new import, not an old one.
        bruksdata.deleteByPeriode(periode);
        for (BruksdataImport gammel : importer.findByPeriodeAndStatusNot(periode, Importstatus.AVVIST.name())) {
            if (Objects.equals(gammel.id(), fv.stagetImportId())) {
                continue;
            }
            importer.save(gammel.medStatus(Importstatus.AVVIST.name()));
            audit.logg(Handling.AVVISTE, "BRUKSDATA_IMPORT", gammel.id(),
                    Map.of("periode", fv.periode().toString(), "aarsak", "erstattet av ny import"));
        }

        BruksdataImport lagret = importer.save(nyImportrad(fv, bruker));

        List<Bruksdata> medImport = fv.gyldige().stream()
                .map(b -> new Bruksdata(null, lagret.id(), b.periode(), b.organisasjonsnummer(),
                        b.produktId(), b.type(), b.antall(), b.belop()))
                .toList();
        bruksdata.saveAll(medImport);

        Map<String, Object> detaljer = new LinkedHashMap<>();
        detaljer.put("periode", fv.periode().toString());
        detaljer.put("filnavn", fv.filnavn());
        detaljer.put("kilde", fv.kilde().name());
        detaljer.put("antall", fv.antallGyldige());
        if (lagret.harRaadata()) {
            detaljer.put("raadataSha256", lagret.raadataSha256());
        }
        audit.logg(Handling.IMPORTERTE, "BRUKSDATA_IMPORT", lagret.id(), detaljer);
        return lagret;
    }

    /** The import row to persist: a new CSV row, a new DWH row (archiving the payload), or the staged row. */
    private BruksdataImport nyImportrad(Forhaandsvisning fv, String bruker) {
        LocalDate periode = fv.periode().førsteDag();
        OffsetDateTime nå = OffsetDateTime.now();
        if (fv.kilde() == Kilde.CSV) {
            return BruksdataImport.csv(fv.filnavn(), periode, Importstatus.VALIDERT.name(), fv.antallGyldige(), bruker, nå);
        }
        Raadata r = Objects.requireNonNull(fv.raadata(), "DWH-import uten rådata");
        if (!r.erArkivert()) {
            r = arkiver(fv.periode(), r);
        }
        return new BruksdataImport(fv.stagetImportId(), fv.filnavn(), periode, Kilde.DWH.name(),
                Importstatus.VALIDERT.name(), fv.antallGyldige(), bruker, nå, r.url(), r.sha256(), r.hentetAt());
    }

    /** Archive a DWH payload under the period; returns it with location and checksum. */
    Raadata arkiver(Periode periode, Raadata r) {
        String navn = "bruksdata/" + periode + "/dwh-" + r.hentetAt().toInstant().toEpochMilli() + ".json";
        FileArchive.Arkivert a = arkiv.lagre(navn, r.innhold());
        return new Raadata(r.innhold(), r.hentetAt(), a.blobUrl(), a.sha256());
    }

    @Transactional(readOnly = true)
    public byte[] raadata(Long importId) {
        BruksdataImport imp = hentImport(importId);
        if (!imp.harRaadata()) {
            throw new Regelbrudd("Importen har ingen arkiverte rådata (kun datavarehus-importer har det)");
        }
        return arkiv.hent(imp.raadataUrl());
    }

    @Transactional(readOnly = true)
    public List<BruksdataImport> alleImporter() {
        return importer.findAllByOrderByLastetAtDesc();
    }

    @Transactional(readOnly = true)
    public BruksdataImport hentImport(Long id) {
        return importer.findById(id).orElseThrow(() -> new Regelbrudd("Fant ikke import " + id));
    }

    @Transactional(readOnly = true)
    public List<Bruksdata> rader(Long importId) {
        return bruksdata.findByImportId(importId);
    }

    /** All usage for a period, for the generation engine (billing depends on this public API). */
    @Transactional(readOnly = true)
    public List<Bruksdata> bruksdataFor(Periode periode) {
        return bruksdata.findByPeriode(periode.førsteDag());
    }

    /**
     * Month-over-month sanity check (FR-007): warn when a product's total moves more than the configured
     * share from the previous period's committed usage, or disappears. Never blocks.
     */
    private List<String> avvikMotForrigeMaaned(Periode periode, List<ProduktSum> naa, Map<String, Long> kodeTilId) {
        List<Bruksdata> forrige = bruksdata.findByPeriode(periode.forrige().førsteDag());
        if (forrige.isEmpty()) {
            return List.of();
        }
        Map<String, ProduktSum> forrigeSummer = new LinkedHashMap<>();
        summer(forrige, kodeTilId).forEach(s -> forrigeSummer.put(s.produktkode(), s));
        Map<String, ProduktSum> naaSummer = new LinkedHashMap<>();
        naa.forEach(s -> naaSummer.put(s.produktkode(), s));

        BigDecimal grense = BigDecimal.valueOf(dwh.avviksgrenseProsent());
        List<String> advarsler = new ArrayList<>();
        forrigeSummer.forEach((kode, f) -> {
            ProduktSum n = naaSummer.get(kode);
            if (n == null) {
                advarsler.add("Produktet " + kode + " hadde bruk i " + periode.forrige() + ", men mangler nå");
                return;
            }
            BigDecimal før = f.sumAntall().add(f.sumBelop());
            BigDecimal etter = n.sumAntall().add(n.sumBelop());
            if (før.signum() == 0) {
                return;
            }
            BigDecimal prosent = etter.subtract(før).multiply(BigDecimal.valueOf(100))
                    .divide(før, 0, RoundingMode.HALF_UP);
            if (prosent.abs().compareTo(grense) > 0) {
                advarsler.add("Produktet " + kode + " endret seg " + (prosent.signum() > 0 ? "+" : "") + prosent
                        + " % fra " + periode.forrige() + " (" + før.toPlainString() + " → " + etter.toPlainString() + ")");
            }
        });
        return advarsler;
    }

    private List<ProduktSum> summer(List<Bruksdata> gyldige, Map<String, Long> kodeTilId) {
        Map<Long, String> idTilKode = new LinkedHashMap<>();
        kodeTilId.forEach((kode, id) -> idTilKode.put(id, kode));
        Map<Long, long[]> antallPerProdukt = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> sumPerProdukt = new LinkedHashMap<>();
        for (Bruksdata b : gyldige) {
            antallPerProdukt.computeIfAbsent(b.produktId(), k -> new long[1])[0]++;
            BigDecimal[] s = sumPerProdukt.computeIfAbsent(b.produktId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (b.antall() != null) {
                s[0] = s[0].add(b.antall());
            }
            if (b.belop() != null) {
                s[1] = s[1].add(b.belop());
            }
        }
        List<ProduktSum> resultat = new ArrayList<>();
        antallPerProdukt.forEach((id, antall) -> resultat.add(new ProduktSum(
                idTilKode.getOrDefault(id, String.valueOf(id)), antall[0],
                sumPerProdukt.get(id)[0], sumPerProdukt.get(id)[1])));
        resultat.sort((a, b) -> a.produktkode().compareTo(b.produktkode()));
        return resultat;
    }

    private static LocalDate parseDato(String tekst) {
        if (tekst == null) {
            return null;
        }
        try {
            return LocalDate.parse(tekst.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Bruksdatatype parseType(String tekst) {
        if (tekst == null) {
            return null;
        }
        try {
            return Bruksdatatype.valueOf(tekst.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal krevIkkeNegativ(String tekst, String felt) {
        if (tekst == null || tekst.isBlank()) {
            throw new UgyldigVerdi("mangler " + felt);
        }
        BigDecimal verdi;
        try {
            verdi = new BigDecimal(tekst.trim());
        } catch (NumberFormatException e) {
            throw new UgyldigVerdi("ugyldig " + felt + ": " + tekst);
        }
        if (verdi.signum() < 0) {
            throw new UgyldigVerdi("negativ " + felt + ": " + tekst);
        }
        return verdi;
    }

    /** Internal signal for a bad numeric field; converted to an {@link AvvistRad}. */
    private static final class UgyldigVerdi extends RuntimeException {
        UgyldigVerdi(String melding) {
            super(melding);
        }
    }
}
