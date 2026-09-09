package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Kunde;
import no.digdir.forsystem.registry.KundeReferanse;
import no.digdir.forsystem.registry.KundeReferanseRepository;
import no.digdir.forsystem.registry.KundeRepository;
import no.digdir.forsystem.registry.KundenummerRegel;
import no.digdir.forsystem.registry.KundenummerRegelRepository;
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

/** Fixture helpers for building registries + usage in billing tests, on the shared Testcontainer. */
abstract class BillingFixture extends IntegrationTest {

    static final Periode JAN = Periode.av(2027, 1);

    @Autowired FakturakjoringService tjeneste;
    @Autowired FakturakjoringRepository kjoringer;
    @Autowired ProduktRepository produkter;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired PrisRepository priser;
    @Autowired KundeRepository kunder;
    @Autowired KundeReferanseRepository referanser;
    @Autowired KundenummerRegelRepository regler;
    @Autowired BruksdataImportRepository importer;
    @Autowired BruksdataRepository bruksdata;

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

    Kunde kunde(String orgnr, String navn, String avtalestatus, String fakturamottaker) {
        return kunder.save(new Kunde(null, orgnr, navn, fakturamottaker, avtalestatus, "MANUELL", "test",
                OffsetDateTime.now()));
    }

    KundenummerRegel regel(Long kundeId, String kundenummer, String produktKode, String servicekode,
                           String tilleggstekst) {
        Long pid = produktKode == null ? null : produktId(produktKode);
        return regler.save(new KundenummerRegel(null, kundeId, kundenummer, pid, servicekode, tilleggstekst));
    }

    KundeReferanse referanse(Long kundeId, String produktKode, String ref, String best) {
        Long pid = produktKode == null ? null : produktId(produktKode);
        return referanser.save(new KundeReferanse(null, kundeId, pid, ref, best));
    }

    Long nyImport(Periode periode) {
        return importer.save(new BruksdataImport(null, "test.csv", periode.førsteDag(), "CSV", "VALIDERT",
                0, "test", OffsetDateTime.now())).id();
    }

    void bruk(Long importId, Periode periode, String orgnr, String kode, Bruksdatatype type,
              String antall, String belop) {
        bruksdata.save(new Bruksdata(null, importId, periode.førsteDag(), orgnr, produktId(kode), type.name(),
                antall == null ? null : new BigDecimal(antall),
                belop == null ? null : new BigDecimal(belop)));
    }
}
