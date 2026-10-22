package no.digdir.forsystem.billing;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Godkjenn, Forkast and Eksporter ask for confirmation in a dialog first (specs/004 FR-012). Each
 * trigger is still a plain submit button of its form, so the action works without JS (FR-013).
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KjoringBekreftelseIT extends BillingFixture {

    @Autowired MockMvc mvc;

    private Fakturakjoring generertKjoring() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        return tjeneste.generer(JAN);
    }

    @Test
    void generertKjoringBekrefterGodkjennOgForkast() throws Exception {
        var kj = generertKjoring();
        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type=\"submit\" data-bekreft=\"bekreft-godkjenn\"")))
                .andExpect(content().string(containsString("<dialog class=\"ds-dialog\" id=\"bekreft-godkjenn\"")))
                .andExpect(content().string(containsString("data-bekreft=\"bekreft-forkast\"")))
                .andExpect(content().string(containsString("<dialog class=\"ds-dialog\" id=\"bekreft-forkast\"")))
                .andExpect(content().string(containsString("Godkjenne kjøringen for januar 2027?")));
    }

    @Test
    void godkjentKjoringBekrefterEksport() throws Exception {
        var kj = generertKjoring();
        tjeneste.godkjenn(kj.id());
        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-bekreft=\"bekreft-eksporter\"")))
                .andExpect(content().string(containsString("<dialog class=\"ds-dialog\" id=\"bekreft-eksporter\"")));
    }
}
