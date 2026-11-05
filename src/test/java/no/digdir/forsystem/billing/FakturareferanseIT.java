package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.registry.KundeReferanse;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fakturareferanse and bestillingsnummer on the invoice (specs/012): an empty bestillingsnummer takes the
 * fakturareferanse at generation without ever adding an LG04 split, and an invoice line without fakturareferanse
 * is a warning and a row in the list on the Kontroller tab.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class FakturareferanseIT extends BillingFixture {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private static final String KODE = "MANGLER_FAKTURAREFERANSE";

    @Autowired EksportService eksport;
    @Autowired FileArchive arkiv;
    @Autowired MockMvc mvc;

    /** melding 1,00 and formidling 2,00 per unit, no threshold. */
    private void priser() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "2.0000");
    }

    /** One customer, one kundenummer, usage on melding and formidling. */
    private Long kundeMedToProdukter(String orgnr, String navn, String kundenummer, Long importId) {
        Long k = kunde(orgnr, navn, "AKTIV", null).id();
        regel(k, kundenummer, null, null, null);
        bruk(importId, JAN, orgnr, "melding", Bruksdatatype.BRUKSVOLUM, "30", null);
        bruk(importId, JAN, orgnr, "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        return k;
    }

    private List<Fakturalinje> linjer(Fakturakjoring kj) {
        return tjeneste.fakturaer(kj.id()).stream().flatMap(f -> tjeneste.linjer(f.id()).stream()).toList();
    }

    private List<Kontrollfunn> funn(Fakturakjoring kj, String kode) {
        return tjeneste.funn(kj.id()).stream().filter(f -> kode.equals(f.kode())).toList();
    }

    /** The LG04 order header lines (line number 0) of the exported run. */
    private List<String> lg04Hoder(Fakturakjoring kj) {
        godkjennEtterAksept(kj.id());
        eksport.eksporter(kj.id());
        Eksportfil lg04 = tjeneste.eksportfiler(kj.id()).stream()
                .filter(f -> "LG04".equals(f.type())).findFirst().orElseThrow();
        return Arrays.stream(new String(arkiv.hent(lg04.blobUrl()), WIN_1252).split("\n"))
                .filter(l -> !l.isEmpty() && l.charAt(2493) == '0').toList();
    }

    private static String fakturareferanse(String hode) {
        return hode.substring(25, 50).trim();
    }

    private static String bestillingsnummer(String hode) {
        return hode.substring(2368, 2393).trim();
    }

    // ---- US2: bestillingsnummer = fakturareferanse when empty ---------------------------------------------

    @Test
    void anEmptyBestillingsnummerTakesTheFakturareferanseOnTheInvoiceNotInTheRegistry() {
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", null);
        var kj = tjeneste.generer(JAN);

        assertThat(linjer(kj)).hasSize(2).allSatisfy(l -> {
            assertThat(l.fakturareferanse()).isEqualTo("REF-A");
            assertThat(l.bestillingsnummer()).isEqualTo("REF-A");
        });
        assertThat(referanser.findByKundeId(k)).singleElement()
                .satisfies(r -> assertThat(r.bestillingsnummer()).isNull());   // registry untouched
        assertThat(funn(kj, KODE)).isEmpty();

        assertThat(lg04Hoder(kj)).singleElement().satisfies(h -> {
            assertThat(fakturareferanse(h)).isEqualTo("REF-A");
            assertThat(bestillingsnummer(h)).isEqualTo("REF-A");
        });
    }

    @Test
    void aBlankBestillingsnummerAlsoTakesTheFakturareferanse() {
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanser.save(new KundeReferanse(null, k, null, "REF-A", "   "));
        var kj = tjeneste.generer(JAN);
        assertThat(linjer(kj)).extracting(Fakturalinje::bestillingsnummer).containsOnly("REF-A");
    }

    @Test
    void aGivenBestillingsnummerIsKept() {
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", "PO-1");
        var kj = tjeneste.generer(JAN);
        assertThat(linjer(kj)).extracting(Fakturalinje::bestillingsnummer).containsOnly("PO-1");
        assertThat(lg04Hoder(kj)).singleElement().satisfies(h -> assertThat(bestillingsnummer(h)).isEqualTo("PO-1"));
    }

    @Test
    void theFallbackNeverSplitsAnInvoiceAndJoinsWhatOnlyDifferedByTheEmptyField() {
        // melding has REF-A/REF-A written out, the default REF-A/empty: one reference after the fallback.
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", null);
        referanse(k, "melding", "REF-A", "REF-A");
        var kj = tjeneste.generer(JAN);

        assertThat(funn(kj, "DELT_REFERANSE")).isEmpty();
        assertThat(lg04Hoder(kj)).singleElement().satisfies(h -> assertThat(bestillingsnummer(h)).isEqualTo("REF-A"));
    }

    @Test
    void differentFakturareferanserStillSplitWithTheirOwnBestillingsnummer() {
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", null);
        referanse(k, "melding", "REF-M", null);
        var kj = tjeneste.generer(JAN);

        assertThat(funn(kj, "DELT_REFERANSE")).singleElement()
                .satisfies(f -> assertThat(f.melding()).contains("2 ulike referanser", "REF-A / REF-A", "REF-M / REF-M"));
        assertThat(lg04Hoder(kj)).extracting(FakturareferanseIT::fakturareferanse, FakturareferanseIT::bestillingsnummer)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("REF-A", "REF-A"),
                        org.assertj.core.groups.Tuple.tuple("REF-M", "REF-M"));
    }

    // ---- US1: missing fakturareferanse ------------------------------------------------------------------

    @Test
    void anInvoiceWithoutFakturareferanseGetsOneWarningNamingCustomerAndProducts() {
        priser();
        Long imp = nyImport(JAN);
        kundeMedToProdukter("100000001", "Kunde Uten", "KU", imp);
        Long med = kundeMedToProdukter("100000002", "Kunde Med", "KM", imp);
        referanse(med, null, "REF-M", null);
        var kj = tjeneste.generer(JAN);

        assertThat(funn(kj, KODE)).singleElement().satisfies(f -> {
            assertThat(f.alvorlighet()).isEqualTo("ADVARSEL");
            assertThat(f.fakturaId()).isNotNull();
            assertThat(f.melding()).contains("Kunde Uten", "100000001", "kundenummer KU", "Formidling", "Melding");
        });
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse();

        assertThat(tjeneste.utenFakturareferanse(kj.id())).singleElement().satisfies(u -> {
            assertThat(u.kundenavn()).isEqualTo("Kunde Uten");
            assertThat(u.organisasjonsnummer()).isEqualTo("100000001");
            assertThat(u.kundenummer()).isEqualTo("KU");
            assertThat(u.antallLinjer()).isEqualTo(2);
            assertThat(u.alleLinjer()).isTrue();
            assertThat(u.sumBelop()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    void aProductRowWithOnlyBestillingsnummerCountsAsMissingForThatProduct() {
        // TODO(OQ-24) default: the product row replaces the customer default as a whole.
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", null);
        referanse(k, "melding", null, "PO-9");
        var kj = tjeneste.generer(JAN);

        assertThat(funn(kj, KODE)).singleElement()
                .satisfies(f -> assertThat(f.melding()).contains("Melding").doesNotContain("Formidling"));
        assertThat(tjeneste.utenFakturareferanse(kj.id())).singleElement().satisfies(u -> {
            assertThat(u.produkter()).containsExactly("Melding");
            assertThat(u.alleLinjer()).isFalse();
        });
        assertThat(linjer(kj)).filteredOn(l -> l.fakturareferanse() == null).singleElement()
                .satisfies(l -> assertThat(l.bestillingsnummer()).isEqualTo("PO-9"));   // no reverse fallback
    }

    @Test
    void eachKundenummerIsItsOwnInvoiceAndFinding() {
        priser();
        Long k = kunde("100000001", "Kunde A", "AKTIV", null).id();
        regel(k, "KA1", null, null, null);
        regel(k, "KA2", "formidling", null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "30", null);
        bruk(imp, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        var kj = tjeneste.generer(JAN);

        assertThat(funn(kj, KODE)).hasSize(2);
        assertThat(tjeneste.utenFakturareferanse(kj.id())).extracting(FakturaUtenReferanse::kundenummer)
                .containsExactly("KA1", "KA2");
    }

    @Test
    void customersWithoutAnInvoiceAreNotListed() {
        settKontoAlle();
        Long v = prisversjoner.save(new Prisversjon(null, "V-" + System.nanoTime(), JAN.førsteDag(), null, "AKTIV",
                new BigDecimal("40.00"))).id();
        pris(v, "melding", "1.0000");
        regel(kunde("100000001", "Liten", "AKTIV", null).id(), "KL", null, null, null);
        regel(kunde("100000002", "Uten bruk", "AKTIV", null).id(), "KB", null, null, null);
        regel(kunde("100000003", "Stor", "AKTIV", null).id(), "KS", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "5", null);
        bruk(imp, JAN, "100000003", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        var kj = tjeneste.generer(JAN);

        assertThat(tjeneste.utenFakturareferanse(kj.id())).extracting(FakturaUtenReferanse::kundenavn)
                .containsExactly("Stor");
        assertThat(funn(kj, KODE)).singleElement().satisfies(f -> assertThat(f.melding()).startsWith("Stor ("));
    }

    @Test
    void theKontrollerTabListsTheInvoicesAndTheListDownloads() throws Exception {
        priser();
        kundeMedToProdukter("100000001", "Kunde Uten", "KU", nyImport(JAN));
        var kj = tjeneste.generer(JAN);

        mvc.perform(get("/kjoringer/{id}/kontroller", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fakturaer uten fakturareferanse")))
                .andExpect(content().string(containsString("Kunde Uten")))
                .andExpect(content().string(containsString(KODE)))
                .andExpect(content().string(containsString("uten-fakturareferanse.xlsx")));

        String csv = new String(mvc.perform(get("/kjoringer/{id}/uten-fakturareferanse.csv", kj.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("uten-fakturareferanse-2027-01-GENERERT.csv")))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("produkter_uten_fakturareferanse").contains("Kunde Uten").contains("KU");

        mvc.perform(get("/kjoringer/{id}/uten-fakturareferanse.xlsx", kj.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")));
    }

    @Test
    void aRunWhereEveryInvoiceHasAReferenceSaysSo() throws Exception {
        priser();
        Long k = kundeMedToProdukter("100000001", "Kunde A", "KA", nyImport(JAN));
        referanse(k, null, "REF-A", null);
        var kj = tjeneste.generer(JAN);
        assertThat(tjeneste.utenFakturareferanse(kj.id())).isEmpty();
        mvc.perform(get("/kjoringer/{id}/kontroller", kj.id()))
                .andExpect(content().string(containsString("Alle fakturaene har fakturareferanse")))
                .andExpect(content().string(not(containsString("uten-fakturareferanse.csv"))));
    }
}
