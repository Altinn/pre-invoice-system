package no.digdir.forsystem.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import no.digdir.forsystem.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer authorization (docs/04 Phase 1): LESER reads but cannot write; FORVALTER writes; CSRF is
 * enforced; business-rule violations render the friendly error page.
 */
@AutoConfigureMockMvc
class WebRoleIT extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(roles = "LESER")
    void leserCanReadProdukter() throws Exception {
        mvc.perform(get("/produkter")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LESER")
    void leserCannotPostProdukter() throws Exception {
        mvc.perform(post("/produkter").with(csrf())
                        .param("kode", "leser-forsok").param("navn", "Nei"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void forvalterCanPostProdukter() throws Exception {
        mvc.perform(post("/produkter").with(csrf())
                        .param("kode", "forvalter-ok").param("navn", "Ja").param("enhet", "transaksjon"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "LESER")
    void leserIsToldItLacksTheRole() throws Exception {
        mvc.perform(post("/produkter").with(csrf()).param("kode", "leser-forsok").param("navn", "Nei"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Ingen tilgang")));
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void postWithoutCsrfIsForbiddenAndExplainedAsAnExpiredPage() throws Exception {
        // A form opened before a redeploy or logout carries a stale token: say so, and reload that page.
        mvc.perform(post("/produkter").param("kode", "ingen-csrf").param("navn", "Nei")
                        .header("Referer", "https://forsystem.example/bruksdata?x=1"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Siden er utløpt")))
                .andExpect(content().string(containsString("href=\"/bruksdata?x=1\"")))
                .andExpect(content().string(not(containsString("teknisk feil"))));
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void expiredPageNeverLinksOffSite() throws Exception {
        mvc.perform(post("/produkter").header("Referer", "https://evil.example//evil.example/x"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Last inn siden på nytt")))
                .andExpect(content().string(not(containsString("evil.example"))));
    }

    @Test
    void unauthenticatedCannotRead() throws Exception {
        mvc.perform(get("/produkter")).andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void regelbruddRendersErrorPage() throws Exception {
        // 'melding' is a seeded product code → duplicate → Regelbrudd → 400 + feil page.
        mvc.perform(post("/produkter").with(csrf()).param("kode", "melding").param("navn", "Dobbel"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("finnes allerede")));
    }
}
