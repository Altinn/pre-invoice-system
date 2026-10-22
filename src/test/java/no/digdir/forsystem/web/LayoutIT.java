package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.digdir.forsystem.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every main page renders the shared Designsystemet layout (specs/004 FR-004..006, FR-021): Bokmål,
 * a skip link to the main content, and a header that marks exactly the current section. The
 * vendored assets are served locally.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = "LESER")
class LayoutIT extends IntegrationTest {

    private static final Pattern GJELDENDE = Pattern.compile("<a href=\"([^\"]+)\" aria-current=\"page\">");

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "/,",
            "/produkter, /produkter",
            "/produkter/kildenavn, /produkter",
            "/prisversjoner, /prisversjoner",
            "/kunder, /kunder",
            "/kunder/ny, /kunder",
            "/bruksdata, /bruksdata",
            "/kjoringer, /kjoringer",
            "/hendelseslogg, /hendelseslogg",
    })
    void sidenHarFellesOppsettOgMarkererSeksjonen(String side, String seksjon) throws Exception {
        String html = mvc.perform(get(side))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"nb\"")))
                .andExpect(content().string(containsString("class=\"ds-skip-link\" href=\"#hovedinnhold\"")))
                .andExpect(content().string(containsString("id=\"hovedinnhold\"")))
                .andExpect(content().string(containsString("data-color-scheme=\"auto\"")))
                .andReturn().getResponse().getContentAsString();

        String meny = html.substring(html.indexOf("aria-label=\"Hovedmeny\""), html.indexOf("</nav>", html.indexOf("aria-label=\"Hovedmeny\"")));
        Matcher m = GJELDENDE.matcher(meny);
        if (seksjon == null) {
            assertThat(m.find()).as("no section is current on %s", side).isFalse();
        } else {
            assertThat(m.find()).as("a section is current on %s", side).isTrue();
            assertThat(m.group(1)).isEqualTo(seksjon);
            assertThat(m.find()).as("only one section is current on %s", side).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/css/designsystemet.css", "/css/digdir-theme.css", "/css/forsystem.css",
            "/css/fonts/Inter-Regular.woff2", "/css/fonts/Inter-Medium.woff2", "/css/fonts/Inter-SemiBold.woff2",
            "/js/designsystemet-web.js", "/js/forsystem.js", "/js/htmx.min.js",
    })
    void vendretRessursLeveresLokalt(String sti) throws Exception {
        mvc.perform(get(sti)).andExpect(status().isOk());
    }
}
