package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.billing.NullstillingService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Without forsystem.testverktoy.enabled (prod), "Nullstill periode" does not exist at all. */
@AutoConfigureMockMvc
class TestverktoyAvIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    @WithMockUser(roles = {"FORVALTER", "GODKJENNER"})
    void resetIsNotThereWhenTheFlagIsOff() throws Exception {
        assertThat(context.getBeanNamesForType(NullstillingService.class)).isEmpty();
        mvc.perform(post("/testverktoy/nullstill-periode").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/kjoringer"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("nullstill"))));
    }
}
