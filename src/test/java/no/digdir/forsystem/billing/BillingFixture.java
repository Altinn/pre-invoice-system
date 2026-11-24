package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Pris;
import no.digdir.forsystem.registry.PrisRepository;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.BruksdataImportRepository;
import no.digdir.forsystem.usage.BruksdataRepository;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixture helpers for building registries + usage in billing tests, on the shared Testcontainer.
 *
 * <p>Customer data comes with the usage since specs/018 ({@code bruksdata_kunde}). The helpers {@link #kunde},
 * {@link #regel} and {@link #referanse} keep the old test vocabulary: they describe what the DWH delivers for an
 * orgnr, and {@link #synkKundedata} writes it as the {@code bruksdata_kunde} row of every import with usage for
 * that orgnr. Only the customer default counts (product-specific rules and references no longer exist); the
 * first rule's kundenummer is the one delivered. Call {@link #generer} instead of {@code tjeneste.generer} so the
 * rows are in place, also for usage imported through {@code UsageImportService}.
 */
abstract class BillingFixture extends IntegrationTest {

    static final Periode JAN = Periode.av(2027, 1);

    @Autowired FakturakjoringService tjeneste;
    @Autowired FakturakjoringRepository kjoringer;
    @Autowired ProduktRepository produkter;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired PrisRepository priser;
    @Autowired JdbcTemplate jdbc;
    @Autowired BruksdataImportRepository importer;
    @Autowired BruksdataRepository bruksdata;

    /**
     * Accept every open warning, then approve — what a reviewer does before «Godkjenn» since specs/013.
     * Tests that check the approval gate itself call {@code tjeneste.godkjenn} directly.
     */
    void godkjennEtterAksept(Long kjoringId) {
        aksepterAlleAdvarsler(kjoringId);
        tjeneste.godkjenn(kjoringId);
    }

    void aksepterAlleAdvarsler(Long kjoringId) {
        tjeneste.funn(kjoringId).stream()
                .filter(Kontrollfunn::aapenAdvarsel)
                .map(Kontrollfunn::kode)
                .distinct()
                .forEach(kode -> tjeneste.settAkseptForKode(kjoringId, kode, true));
    }

    Long produktId(String kode) {
        return produkter.findByKode(kode).orElseThrow().id();
    }

    void settKonto(String kode, String konto) {
        Produkt p = produkter.findByKode(kode).orElseThrow();
        produkter.save(new Produkt(p.id(), p.kode(), p.navn(), 1, konto, "d1", "d2", "d4", p.enhet(), p.aktiv()));
    }

    void settKontoAlle() {
        produkter.findAllByOrderByKode().forEach(p -> settKonto(p.kode(), "3000"));
    }

    Prisversjon aktivVersjon(LocalDate fra, LocalDate til) {
        return prisversjoner.save(new Prisversjon(null, "V-" + System.nanoTime(), fra, til, "AKTIV"));
    }

    void pris(Long versjonId, String kode, String enhetspris) {
        priser.save(new Pris(null, versjonId, produktId(kode), new BigDecimal(enhetspris)));
    }

    /** What the DWH delivers for one orgnr in the tests (see the class comment). */
    record TestKunde(Long id, String organisasjonsnummer, String virksomhetsnavn) {
    }

    private final Map<Long, Kundespek> kundespek = new LinkedHashMap<>();

    private static final class Kundespek {
        final String orgnr;
        final String navn;
        final List<String> kundenummer = new ArrayList<>();
        String fakturareferanse;
        String bestillingsnummer;

        Kundespek(String orgnr, String navn) {
            this.orgnr = orgnr;
            this.navn = navn;
        }
    }

    /** {@code avtalestatus} and {@code fakturamottaker} are ignored: the DWH delivers neither (specs/018). */
    TestKunde kunde(String orgnr, String navn, String avtalestatus, String fakturamottaker) {
        long id = kundespek.size() + 1L;
        kundespek.put(id, new Kundespek(orgnr, navn));
        synkKundedata();
        return new TestKunde(id, orgnr, navn);
    }

    /** The kundenummer the DWH delivers; only the first rule without product counts (specs/018). */
    void regel(Long kundeId, String kundenummer, String produktKode, String servicekode, String tilleggstekst) {
        if (produktKode != null || servicekode != null || tilleggstekst != null) {
            throw new IllegalArgumentException("Produktspesifikke regler, servicekode og tilleggstekst finnes ikke "
                    + "lenger (specs/018)");
        }
        kundespek.get(kundeId).kundenummer.add(kundenummer);
        synkKundedata();
    }

    /** The fakturareferanse/bestillingsnummer the DWH delivers; product-specific ones no longer exist. */
    void referanse(Long kundeId, String produktKode, String ref, String best) {
        if (produktKode != null) {
            throw new IllegalArgumentException("Produktspesifikke referanser finnes ikke lenger (specs/018)");
        }
        Kundespek k = kundespek.get(kundeId);
        k.fakturareferanse = ref;
        k.bestillingsnummer = best;
        synkKundedata();
    }

    /** Write the described customers as {@code bruksdata_kunde} rows of every import with usage for them. */
    void synkKundedata() {
        for (Kundespek k : kundespek.values()) {
            List<Long> importIder = jdbc.queryForList(
                    "select distinct import_id from bruksdata where organisasjonsnummer = ?", Long.class, k.orgnr);
            for (Long importId : importIder) {
                jdbc.update("delete from bruksdata_kunde where import_id = ? and organisasjonsnummer = ?",
                        importId, k.orgnr);
                jdbc.update("insert into bruksdata_kunde (import_id, organisasjonsnummer, virksomhetsnavn, "
                                + "kundenummer, fakturareferanse, bestillingsnummer) values (?, ?, ?, ?, ?, ?)",
                        importId, k.orgnr, k.navn, k.kundenummer.isEmpty() ? null : k.kundenummer.getFirst(),
                        k.fakturareferanse, k.bestillingsnummer);
            }
        }
    }

    /** {@code tjeneste.generer} with the test customers in place. */
    Fakturakjoring generer(Periode periode) {
        synkKundedata();
        return tjeneste.generer(periode);
    }

    /** {@code tjeneste.genererKorrigering} with the test customers in place. */
    Fakturakjoring genererKorrigering(Long sammenligningId, List<Long> endringIder, String aarsak) {
        synkKundedata();
        return tjeneste.genererKorrigering(sammenligningId, endringIder, aarsak);
    }

    Long nyImport(Periode periode) {
        return importer.save(BruksdataImport.csv("test.csv", periode.førsteDag(), "VALIDERT",
                0, "test", OffsetDateTime.now())).id();
    }

    void bruk(Long importId, Periode periode, String orgnr, String kode, Bruksdatatype type,
              String antall, String belop) {
        bruksdata.save(new Bruksdata(null, importId, periode.førsteDag(), orgnr, produktId(kode), type.name(),
                antall == null ? null : new BigDecimal(antall),
                belop == null ? null : new BigDecimal(belop)));
        synkKundedata();
    }
}
