package no.digdir.forsystem.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Avtalestatus;
import no.digdir.forsystem.registry.Kunde;
import no.digdir.forsystem.registry.KundeService;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonService;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.registry.ProduktService;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds a realistic, clearly-fake dataset on startup under the {@code demo} profile, so the whole
 * monthly cycle can be demonstrated locally end-to-end (docs/04 Phase 5). Covers every structural
 * FinMod special case from docs/01. Idempotent — skips if customers already exist.
 *
 * <p>All accounting codes and prices here are LOUD DEMO placeholders (never real values — CLAUDE.md):
 * the price version is named "…(DEMO)" and kontering is "DEMO…". Real kontering/prices are OQ-2.
 */
@Component
@Profile("demo")
class Demodata implements ApplicationRunner {

    private static final Periode JANUAR_2027 = Periode.av(2027, 1);

    private final ProduktService produktService;
    private final ProduktRepository produkter;
    private final PrisversjonService prisversjoner;
    private final KundeService kunder;
    private final UsageImportService usage;

    Demodata(ProduktService produktService, ProduktRepository produkter, PrisversjonService prisversjoner,
             KundeService kunder, UsageImportService usage) {
        this.produktService = produktService;
        this.produkter = produkter;
        this.prisversjoner = prisversjoner;
        this.kunder = kunder;
        this.usage = usage;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!kunder.alle().isEmpty()) {
            return; // already seeded
        }
        settDemoKontering();
        aktivPrisliste();
        seedKunder();
        importerBruk();
    }

    private void settDemoKontering() {
        int i = 90000;
        for (Produkt p : produkter.findAllByOrderByKode()) {
            produktService.oppdater(p.id(), p.navn(), ++i, "DEMOKONTO",
                    "DEMODIM1", "DEMODIM2", "DEMODIM4", p.enhet(), true);
        }
    }

    private void aktivPrisliste() {
        Prisversjon v = prisversjoner.opprettUtkast("Prisliste 2027 (DEMO)", LocalDate.of(2027, 1, 1), null);
        // Demo unit prices (NOK) — placeholders, not the published 2027 list.
        settPris(v, "melding", "0.85");
        settPris(v, "formidling", "2.50");
        settPris(v, "varsling", "0.60");
        settPris(v, "autorisasjon", "1.20");
        settPris(v, "studio", "150.00");
        settPris(v, "appinfra", "1.00");
        prisversjoner.aktiver(v.id());
    }

    private void settPris(Prisversjon v, String kode, String pris) {
        prisversjoner.settPris(v.id(), produktId(kode), new BigDecimal(pris));
    }

    private void seedKunder() {
        // Normal customer, several products under one kundenummer.
        Long oslo = kunder.opprett("958935420", "Oslo kommune", null, Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(oslo, "KN-1001", null, null, null);

        // Deviating recipient: usage on one org, invoiced to another legal entity.
        Long helgeland = kunder.opprett("912345678", "Digitale Helgeland", "964338442", Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(helgeland, "KN-1002", null, null, null);

        // Two kundenummer for one customer (different servicekode per product) → two invoices.
        Long udir = kunder.opprett("970018131", "Utdanningsdirektoratet", null, Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(udir, "KN-2001", produktId("melding"), "UDIR-A", null);
        kunder.leggTilRegel(udir, "KN-2002", produktId("autorisasjon"), "UDIR-B", null);

        // Shared kundenummer across two customers, distinguished by tilleggstekst.
        Long polDir = kunder.opprett("915429785", "Politidirektoratet", null, Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(polDir, "KN-3000", null, null, "Politidirektoratet");
        Long politiet = kunder.opprett("983998006", "Politiet", null, Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(politiet, "KN-3000", null, null, "Politi- og lensmannsetaten");

        // Product-specific fakturareferanse + bestillingsnummer.
        Long trondelag = kunder.opprett("817920632", "Trøndelag fylkeskommune", null, Avtalestatus.AKTIV).id();
        kunder.leggTilRegel(trondelag, "KN-1003", null, null, null);
        kunder.leggTilReferanse(trondelag, produktId("appinfra"), "REF-APP-2027", "BEST-99");
    }

    private void importerBruk() {
        try (InputStream csv = new ClassPathResource("demo/bruk-2027-01.csv").getInputStream()) {
            var forhaandsvisning = usage.forhaandsvis(JANUAR_2027, "bruk-2027-01.csv", csv);
            usage.importer(forhaandsvisning);
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese demo-bruksdata", e);
        }
    }

    private Long produktId(String kode) {
        return produkter.findByKode(kode).orElseThrow().id();
    }

    // Kept for readability of the seeded set.
    @SuppressWarnings("unused")
    private static final List<String> DEKKER = List.of(
            "normal", "avvikende-mottaker", "to-kundenummer", "delt-kundenummer", "produktspesifikk-referanse",
            "gjennomstrømningskostnader");
}
