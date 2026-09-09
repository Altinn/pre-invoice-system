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
    void manglerKundeFires() {
        settKontoAlle();
        standardVersjon();
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000009", "melding", Bruksdatatype.BRUKSVOLUM, "1", null); // no kunde
        var kj = tjeneste.generer(JAN);
        assertThat(koder(kj.id())).contains("MANGLER_KUNDE");
        assertThat(tjeneste.harBlokkerende(kj.id())).isTrue();
    }

    @Test
    void manglerKundenummerFires() {
        settKontoAlle();
        standardVersjon();
        kunde("100000001", "Uten regel", "AKTIV", null); // no kundenummer_regel
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN);
        assertThat(koder(kj.id())).contains("MANGLER_KUNDENUMMER");
    }

    @Test
    void inaktivAvtaleFires() {
        settKontoAlle();
        standardVersjon();
        Long k = kunde("100000001", "Inaktiv", "INAKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN);
        assertThat(koder(kj.id())).contains("INAKTIV_AVTALE");
    }

    @Test
    void manglerPrisFires() {
        settKontoAlle();
        aktivVersjon(LocalDate.of(2027, 1, 1), null); // no price for the used product
        Long k = kunde("100000001", "Uten pris", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN);
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
        var kj = tjeneste.generer(JAN);
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
        var blokkert = tjeneste.generer(JAN);
        assertThatThrownBy(() -> tjeneste.godkjenn(blokkert.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("blokkerende");

        // Fix the data (set kontering), forkast, regenerate → approval succeeds.
        tjeneste.forkast(blokkert.id());
        settKontoAlle();
        var ren = tjeneste.generer(JAN);
        assertThat(tjeneste.harBlokkerende(ren.id())).isFalse();
        tjeneste.godkjenn(ren.id());
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
        var kj = tjeneste.generer(JAN);
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
        tjeneste.generer(des);

        // January: existing customer jumps > 30 %, plus a brand-new customer.
        Long impJan = nyImport(JAN);
        bruk(impJan, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "200", null); // 100 → 200 (+100 %)
        Long kNy = kunde("100000002", "Ny kunde", "AKTIV", null).id();
        regel(kNy, "KN2", null, null, null);
        bruk(impJan, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "5", null);

        var kj = tjeneste.generer(JAN);
        assertThat(koder(kj.id())).contains("STORT_AVVIK", "NY_KUNDE");
    }
}
