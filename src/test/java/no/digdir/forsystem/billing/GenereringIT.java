package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;

/**
 * The generation engine on a fixture exercising every FinMod rule from docs/01: normal customer,
 * deviating recipient, product-specific reference, two kundenummer (→ two invoices), a shared
 * kundenummer across two customers, pass-through cost lines, and rounding. Also determinism.
 */
class GenereringIT extends BillingFixture {

    private void byggFixture() {
        settKontoAlle();
        var v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.6650");
        pris(v, "formidling", "2.0000");
        pris(v, "varsling", "1.0000");
        pris(v, "studio", "1.0000");
        pris(v, "autorisasjon", "1.0000");
        Long imp = nyImport(JAN);

        Long k1 = kunde("100000001", "Normal AS", "AKTIV", null).id();
        regel(k1, "KN1", null, null, null);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null); // 3 × 1.6650 = 4.995 → 5.00

        Long k2 = kunde("100000002", "Deviating AS", "AKTIV", "999999999").id();
        regel(k2, "KN2", null, null, null);
        bruk(imp, JAN, "100000002", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);

        Long k3 = kunde("100000003", "Ref AS", "AKTIV", null).id();
        regel(k3, "KN3", null, null, null);
        referanse(k3, null, "REF-V", "BEST-V");
        bruk(imp, JAN, "100000003", "varsling", Bruksdatatype.BRUKSVOLUM, "2", null);

        Long k4 = kunde("100000004", "Udir", "AKTIV", null).id();
        regel(k4, "KN4", null, null, null);
        bruk(imp, JAN, "100000004", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        bruk(imp, JAN, "100000004", "studio", Bruksdatatype.BRUKSVOLUM, "1", null);

        Long k5a = kunde("100000005", "Politi A", "AKTIV", null).id();
        regel(k5a, "KN-SHARED", null, null, null);
        bruk(imp, JAN, "100000005", "autorisasjon", Bruksdatatype.BRUKSVOLUM, "1", null);
        Long k5b = kunde("100000006", "Politi B", "AKTIV", null).id();
        regel(k5b, "KN-SHARED", null, null, null);
        bruk(imp, JAN, "100000006", "autorisasjon", Bruksdatatype.BRUKSVOLUM, "1", null);

        Long k6 = kunde("100000007", "Azure AS", "AKTIV", null).id();
        regel(k6, "KN6", null, null, null);
        bruk(imp, JAN, "100000007", "appinfra", Bruksdatatype.AZURE_KOSTNAD, null, "1000.00");
        bruk(imp, JAN, "100000007", "varsling", Bruksdatatype.SMS_KOSTNAD, null, "50.50");
    }

    @Test
    void generatesExpectedInvoicesLinesSnapshotsAndRounding() {
        byggFixture();
        var kj = generer(JAN);
        var fakturaer = tjeneste.fakturaer(kj.id());

        // One invoice per orgnr (specs/018): K1..K4,K5a,K5b,K6 = 7, ordre_nr sequential by orgnr.
        assertThat(fakturaer).hasSize(7);
        assertThat(fakturaer).extracting(Faktura::ordreNr).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(fakturaer).extracting(Faktura::kundenummer)
                .containsExactly("KN1", "KN2", "KN3", "KN4", "KN-SHARED", "KN-SHARED", "KN6");

        // Rounding: 3 × 1.6650 = 4.995 → 5.00 (half-up).
        Faktura k1 = fakturaer.get(0);
        assertThat(tjeneste.linjer(k1.id())).singleElement()
                .satisfies(l -> assertThat(l.belop()).isEqualByComparingTo("5.00"));

        // The organisation itself receives the invoice: the DWH carries no deviating recipient (specs/018).
        assertThat(fakturaer).allSatisfy(f -> assertThat(f.fakturamottakerOrgnr()).isEqualTo(f.organisasjonsnummer()));

        // Reference snapshot.
        assertThat(tjeneste.linjer(fakturaer.get(2).id())).singleElement().satisfies(l -> {
            assertThat(l.fakturareferanse()).isEqualTo("REF-V");
            assertThat(l.bestillingsnummer()).isEqualTo("BEST-V");
        });

        // One kundenummer per orgnr: both products on one invoice, no servicekode.
        assertThat(tjeneste.linjer(fakturaer.get(3).id())).hasSize(2)
                .allSatisfy(l -> assertThat(l.servicekode()).isNull());

        // A shared kundenummer still gives one invoice per organisation; there is no tilleggstekst.
        assertThat(fakturaer.get(4).organisasjonsnummer()).isEqualTo("100000005");
        assertThat(fakturaer.get(5).organisasjonsnummer()).isEqualTo("100000006");
        assertThat(fakturaer).allSatisfy(f -> assertThat(f.tilleggstekst()).isNull());

        // Pass-through cost lines: no antall/enhetspris/pris, belop from usage; total = exact sum.
        Faktura azure = fakturaer.get(6);
        var azureLinjer = tjeneste.linjer(azure.id());
        assertThat(azureLinjer).hasSize(2)
                .allSatisfy(l -> {
                    assertThat(l.antall()).isNull();
                    assertThat(l.enhetspris()).isNull();
                    assertThat(l.prisId()).isNull();
                });
        assertThat(azure.sumBelop()).isEqualByComparingTo("1050.50");

        // Kontering set + all priced + every orgnr has a kundenummer → no blocking findings.
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse();
    }

    @Test
    void generationIsDeterministic() {
        byggFixture();
        var forste = signatur(generer(JAN).id());
        // Re-run after discarding: same inputs must yield identical invoices and lines.
        tjeneste.forkast(tjeneste.alle().get(0).id());
        var andre = signatur(generer(JAN).id());
        assertThat(andre).isEqualTo(forste);
    }

    /** A structural signature of a run's invoices and lines, excluding ids and the random UUID. */
    private List<String> signatur(Long kjoringId) {
        return tjeneste.fakturaer(kjoringId).stream()
                .flatMap(f -> {
                    String fakturaDel = f.ordreNr() + "|" + f.kundenummer() + "|" + f.tilleggstekst()
                            + "|" + f.fakturamottakerOrgnr() + "|" + f.sumBelop().stripTrailingZeros();
                    return tjeneste.linjer(f.id()).stream()
                            .map(l -> fakturaDel + "#" + l.produktId() + "|" + l.beskrivelse() + "|"
                                    + l.belop().stripTrailingZeros() + "|" + l.servicekode() + "|"
                                    + l.fakturareferanse());
                })
                .sorted()
                .toList();
    }
}
