package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;

/** Each control fires on the data designed for it; BLOKKERENDE prevents approval. */
class GenereringControlsIT extends BillingFixture {

    private Long standardVersjon() {
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        return v;
    }

    private List<String> koder(Long kjoringId) {
        return tjeneste.funn(kjoringId).stream().map(Kontrollfunn::kode).toList();
    }

    @Test
    void usageWithoutCustomerDataGivesMissingKundenummerNotMissingCustomer() {
        // specs/018: there is no registry, so an orgnr the DWH delivered nothing for lacks a kundenummer.
        settKontoAlle();
        standardVersjon();
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000009", "melding", Bruksdatatype.BRUKSVOLUM, "1", null); // no customer values
        var kj = generer(JAN);
        assertThat(koder(kj.id())).containsExactly("MANGLER_KUNDENUMMER").doesNotContain("MANGLER_KUNDE");
        assertThat(tjeneste.funn(kj.id()).getFirst().melding())
                .isEqualTo("Importen har ikke kundenummer for 100000009");
        assertThat(tjeneste.harBlokkerende(kj.id())).isTrue();
        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id())).isInstanceOf(Regelbrudd.class);
    }

    @Test
    void customerValuesOfTheImportReachTheInvoiceAndItsLines() {
        settKontoAlle();
        Long v = standardVersjon();
        pris(v, "varsling", "1.0000");
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000001", "varsling", Bruksdatatype.BRUKSVOLUM, "100", null);
        jdbc.update("insert into bruksdata_kunde (import_id, organisasjonsnummer, virksomhetsnavn, kundenummer, "
                + "fakturareferanse, bestillingsnummer) values (?, '100000001', 'alfa kommune', 'K-A', 'REF-A', null)",
                imp);

        var kj = generer(JAN);

        assertThat(tjeneste.fakturaer(kj.id())).singleElement().satisfies(f -> {
            assertThat(f.organisasjonsnummer()).isEqualTo("100000001");
            assertThat(f.kundenavn()).isEqualTo("alfa kommune");   // as delivered, never prettified
            assertThat(f.kundenummer()).isEqualTo("K-A");
            assertThat(f.fakturamottakerOrgnr()).isEqualTo("100000001");
            assertThat(f.tilleggstekst()).isNull();
            assertThat(tjeneste.linjer(f.id())).hasSize(2).allSatisfy(l -> {
                assertThat(l.fakturareferanse()).isEqualTo("REF-A");
                assertThat(l.bestillingsnummer()).isEqualTo("REF-A");   // fallback (specs/012)
                assertThat(l.servicekode()).isNull();
            });
        });
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse();
    }

    @Test
    void conflictingCustomerValuesBlockWithoutPickingOne() {
        settKontoAlle();
        standardVersjon();
        Long k = kunde("100000002", "Annen", "AKTIV", null).id();
        regel(k, "K-B", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        jdbc.update("insert into bruksdata_kunde (import_id, organisasjonsnummer, virksomhetsnavn, kundenummer, "
                + "motstridende) values (?, '100000001', 'alfa', '5663', '{\"kundenummer: 5663 | 5664\"}')", imp);

        var kj = generer(JAN);

        assertThat(tjeneste.funn(kj.id())).filteredOn(f -> "MOTSTRIDENDE_KUNDEDATA".equals(f.kode()))
                .singleElement().satisfies(f -> {
                    assertThat(f.alvorlighet()).isEqualTo("BLOKKERENDE");
                    assertThat(f.melding()).isEqualTo(
                            "Importen har motstridende kundedata for alfa (100000001): kundenummer: 5663 | 5664");
                });
        // Only the other organisation is invoiced.
        assertThat(tjeneste.fakturaer(kj.id())).extracting(Faktura::organisasjonsnummer).containsExactly("100000002");
        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id())).isInstanceOf(Regelbrudd.class);
    }

    @Test
    void aReferenceLongerThanTheLg04FieldBlocksInsteadOfBeingShortened() {
        settKontoAlle();
        standardVersjon();
        Long k = kunde("100000001", "Alfa", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        referanse(k, null, "Altinn v/Otto Scharff, avd. 12", null);   // 30 characters
        Long ok = kunde("100000002", "Beta", "AKTIV", null).id();
        regel(ok, "KN2", null, null, null);
        referanse(ok, null, "1234567890123456789012345", "PO-1");     // exactly 25: fits
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);

        var kj = generer(JAN);

        assertThat(tjeneste.funn(kj.id())).filteredOn(f -> "FOR_LANG_REFERANSE".equals(f.kode()))
                .singleElement().satisfies(f -> {
                    assertThat(f.alvorlighet()).isEqualTo("BLOKKERENDE");
                    assertThat(f.melding()).isEqualTo("Importen har for lang fakturareferanse «Altinn v/Otto Scharff, "
                            + "avd. 12» (30 tegn) for Alfa (100000001). LG04-filen har plass til 25 tegn.");
                });
        assertThat(tjeneste.fakturaer(kj.id())).extracting(Faktura::organisasjonsnummer).containsExactly("100000002");
        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id())).isInstanceOf(Regelbrudd.class);
    }

    @Test
    void aNewImportChangesTheValuesOnRegenerationButNotOnAnApprovedRun() {
        settKontoAlle();
        standardVersjon();
        Long k = kunde("100000001", "Alfa", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        referanse(k, null, "REF-GAMMEL", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var forste = generer(JAN);
        Long forsteFaktura = tjeneste.fakturaer(forste.id()).getFirst().id();

        tjeneste.forkast(forste.id());
        referanse(k, null, "REF-NY", null);
        var andre = generer(JAN);

        assertThat(tjeneste.linjer(tjeneste.fakturaer(andre.id()).getFirst().id()))
                .extracting(Fakturalinje::fakturareferanse).containsOnly("REF-NY");
        // The earlier run keeps the values it was generated with.
        assertThat(tjeneste.linjer(forsteFaktura)).extracting(Fakturalinje::fakturareferanse).containsOnly("REF-GAMMEL");
    }

    @Test
    void invoiceTotalAboveColumnLimitFailsWithReasonInsteadOfOverflow() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "200.0000");
        Long k = kunde("100000002", "Stor bruker", "AKTIV", null).id();
        regel(k, "K002", null, null, null);
        Long imp = nyImport(JAN);
        // 60M units × 200 kr = 12 billion kr > numeric(12,2).
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "60000000", null);

        assertThatThrownBy(() -> generer(JAN))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("Stor bruker")
                .hasMessageContaining("maksgrensen");
    }

    @Test
    void manglerKundenummerFires() {
        settKontoAlle();
        standardVersjon();
        kunde("100000001", "Uten regel", "AKTIV", null); // no kundenummer_regel
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = generer(JAN);
        assertThat(koder(kj.id())).contains("MANGLER_KUNDENUMMER");
    }

    @Test
    void manglerPrisFires() {
        settKontoAlle();
        aktivVersjon(LocalDate.of(2027, 1, 1), null); // no price for the used product
        Long k = kunde("100000001", "Uten pris", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = generer(JAN);
        assertThat(koder(kj.id())).contains("MANGLER_PRIS");
    }

    @Test
    void manglerKonteringFires() {
        // Do NOT set kontering → the seeded product's konto is null (OQ-2).
        standardVersjon();
        Long k = kunde("100000001", "Uten kontering", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = generer(JAN);
        assertThat(koder(kj.id())).contains("MANGLER_KONTERING");
    }

    @Test
    void blokkerendePreventsApprovalAndFixingClearsIt() {
        // First run: missing kontering blocks approval.
        standardVersjon();
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var blokkert = generer(JAN);
        assertThatThrownBy(() -> tjeneste.godkjenn(blokkert.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("blokkerende");

        // Fix the data (set kontering), forkast, regenerate → approval succeeds.
        tjeneste.forkast(blokkert.id());
        settKontoAlle();
        var ren = generer(JAN);
        assertThat(tjeneste.harBlokkerende(ren.id())).isFalse();
        godkjennEtterAksept(ren.id());
        assertThat(tjeneste.hent(ren.id()).status()).isEqualTo("GODKJENT");
    }

    @Test
    void nullbelopWarningFires() {
        settKontoAlle();
        standardVersjon();
        Long k = kunde("100000001", "Null", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "0", null); // 0 × price = 0.00
        var kj = generer(JAN);
        assertThat(koder(kj.id())).contains("NULLBELOP");
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse(); // advarsel only
    }

    @Test
    void nyKundeAndStortAvvikWarningsUsePreviousPeriod() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2026, 12, 1), null).id();
        pris(v, "melding", "1.0000");

        var des = no.digdir.forsystem.common.Periode.av(2026, 12);
        Long kFast = kunde("100000001", "Fast kunde", "AKTIV", null).id();
        regel(kFast, "KN1", null, null, null);
        Long impDes = nyImport(des);
        bruk(impDes, des, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null); // sum 100.00
        generer(des);

        // January: existing customer jumps > 30 %, plus a brand-new customer.
        Long impJan = nyImport(JAN);
        bruk(impJan, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "200", null); // 100 → 200 (+100 %)
        Long kNy = kunde("100000002", "Ny kunde", "AKTIV", null).id();
        regel(kNy, "KN2", null, null, null);
        bruk(impJan, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "5", null);

        var kj = generer(JAN);
        assertThat(koder(kj.id())).contains("STORT_AVVIK", "NY_KUNDE");
    }
}
