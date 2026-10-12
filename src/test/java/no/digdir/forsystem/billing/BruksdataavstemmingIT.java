package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import no.digdir.forsystem.usage.Bruksdatatype;

/**
 * Usage reconciliation per run (specs/002 US2, FR-009..013, SC-003): every imported row is either on an
 * invoice line or listed with the finding that kept it off.
 */
@AutoConfigureMockMvc
class BruksdataavstemmingIT extends BillingFixture {

    private static final String KUNDE = "100000001";
    private static final String UTEN_REGEL = "100000002";
    private static final String UKJENT = "999999998";
    private static final String INAKTIV = "100000003";

    @Autowired BruksdataavstemmingService service;
    @Autowired MockMvc mvc;

    private Long registre() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "varsling", "0.5000");
        Long k = kunde(KUNDE, "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        return v;
    }

    @Test
    void cleanRunBalancesAndSaysEverythingIsInvoiced() {
        registre();
        Long imp = nyImport(JAN);
        bruk(imp, JAN, KUNDE, "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(imp, JAN, KUNDE, "varsling", Bruksdatatype.SMS_KOSTNAD, null, "12.50");
        var kj = tjeneste.generer(JAN);

        var a = service.forKjoring(kj.id());

        assertThat(a.kilde().id()).isEqualTo(imp);
        assertThat(a.allFakturert()).isTrue();
        assertThat(a.ikkeFakturert()).isEmpty();
        var melding = rad(a, "melding", "BRUKSVOLUM");
        assertThat(melding.importertRader()).isEqualTo(1).isEqualTo(melding.fakturertLinjer());
        assertThat(melding.importertSum()).isEqualByComparingTo("10").isEqualByComparingTo(melding.fakturertSum());
        // Pass-through cost reconciles on amount, not quantity.
        var sms = rad(a, "varsling", "SMS_KOSTNAD");
        assertThat(sms.importertSum()).isEqualByComparingTo("12.50").isEqualByComparingTo(sms.fakturertSum());
        // An active product without usage is still listed, so its absence is visible (T10).
        assertThat(a.rader()).anySatisfy(r -> {
            assertThat(r.produktKode()).isEqualTo("studio");
            assertThat(r.type()).isNull();
            assertThat(r.importertRader()).isZero();
        });
    }

    @Test
    void skippedRowsAreListedWithTheFindingThatBlockedThem() {
        registre();
        kunde(UTEN_REGEL, "Uten regel", "AKTIV", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, KUNDE, "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(imp, JAN, KUNDE, "formidling", Bruksdatatype.BRUKSVOLUM, "4", null);      // no price
        bruk(imp, JAN, UKJENT, "melding", Bruksdatatype.BRUKSVOLUM, "7", null);        // no customer
        bruk(imp, JAN, UTEN_REGEL, "melding", Bruksdatatype.BRUKSVOLUM, "3", null);    // no kundenummer rule
        var kj = tjeneste.generer(JAN);

        var a = service.forKjoring(kj.id());

        assertThat(a.allFakturert()).isFalse();
        assertThat(a.invariantBrudd()).isFalse();
        assertThat(a.ikkeFakturert())
                .extracting(u -> u.organisasjonsnummer() + "/" + u.produktKode() + "=" + u.kode())
                .containsExactlyInAnyOrder(
                        KUNDE + "/formidling=MANGLER_PRIS",
                        UKJENT + "/melding=MANGLER_KUNDE",
                        UTEN_REGEL + "/melding=MANGLER_KUNDENUMMER");
        var melding = rad(a, "melding", "BRUKSVOLUM");
        assertThat(melding.importertRader()).isEqualTo(3);
        assertThat(melding.fakturertLinjer()).isEqualTo(1);
        assertThat(melding.ikkeFakturertRader()).isEqualTo(2);
        assertThat(melding.differanse()).isEqualByComparingTo("10");
        assertThat(a.rader()).allSatisfy(r -> assertThat(r.balanserer()).isTrue());
    }

    @Test
    void inactiveAgreementRowsAreInvoicedButBlockApproval() {
        registre();
        Long k = kunde(INAKTIV, "Inaktiv", "INAKTIV", null).id();
        regel(k, "KN9", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, INAKTIV, "melding", Bruksdatatype.BRUKSVOLUM, "5", null);
        var kj = tjeneste.generer(JAN);

        var a = service.forKjoring(kj.id());

        assertThat(a.ikkeFakturert()).isEmpty();
        assertThat(rad(a, "melding", "BRUKSVOLUM").fakturertLinjer()).isEqualTo(1);
        assertThat(tjeneste.harBlokkerende(kj.id())).isTrue();
    }

    @Test
    void discardedRunStillReconcilesAgainstItsOwnImport() {
        registre();
        Long forste = nyImport(JAN);
        bruk(forste, JAN, KUNDE, "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        var gammel = tjeneste.generer(JAN);
        tjeneste.forkast(gammel.id());
        importer.save(importer.findById(forste).orElseThrow().medStatus("AVVIST"));
        Long andre = nyImport(JAN);
        bruk(andre, JAN, KUNDE, "melding", Bruksdatatype.BRUKSVOLUM, "12", null);
        var ny = tjeneste.generer(JAN);

        assertThat(service.forKjoring(gammel.id()).kilde().id()).isEqualTo(forste);
        assertThat(rad(service.forKjoring(gammel.id()), "melding", "BRUKSVOLUM").importertSum()).isEqualByComparingTo("10");
        assertThat(rad(service.forKjoring(ny.id()), "melding", "BRUKSVOLUM").importertSum()).isEqualByComparingTo("12");
    }

    @Test
    @WithMockUser(username = "leser", roles = "LESER")
    void readerCanOpenTheView() throws Exception {
        registre();
        Long imp = nyImport(JAN);
        bruk(imp, JAN, UKJENT, "melding", Bruksdatatype.BRUKSVOLUM, "7", null);
        bruk(imp, JAN, KUNDE, "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN);

        mvc.perform(get("/kjoringer/{id}/avstemming", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Avstemming bruksdata")))
                .andExpect(content().string(Matchers.containsString("datavarehuset filtrerer bort")))
                .andExpect(content().string(Matchers.containsString("MANGLER_KUNDE")))
                .andExpect(content().string(Matchers.containsString(UKJENT)));
    }

    private static Bruksdataavstemming.Rad rad(Bruksdataavstemming a, String kode, String type) {
        return a.rader().stream().filter(r -> kode.equals(r.produktKode()) && type.equals(r.type()))
                .findFirst().orElseThrow();
    }
}
