package no.digdir.forsystem.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Role enforcement on runs: FORVALTER generates, GODKJENNER approves (docs/04 Phase 3). */
@AutoConfigureMockMvc
class WebKjoringRoleIT extends BillingFixture {

    @Autowired MockMvc mvc;

    private void renFixture() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", no.digdir.forsystem.usage.Bruksdatatype.BRUKSVOLUM, "1", null);
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void forvalterCanGenerate() throws Exception {
        renFixture();
        mvc.perform(post("/kjoringer").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "GODKJENNER")
    void godkjennerCannotGenerate() throws Exception {
        mvc.perform(post("/kjoringer").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void forvalterCannotApprove() throws Exception {
        renFixture();
        var kj = tjeneste.generer(JAN);
        mvc.perform(post("/kjoringer/{id}/godkjenn", kj.id()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GODKJENNER")
    void godkjennerCanApprove() throws Exception {
        renFixture();
        var kj = tjeneste.generer(JAN);
        mvc.perform(post("/kjoringer/{id}/godkjenn", kj.id()).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
