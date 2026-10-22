package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A run in five tabs with their own addresses, a progress strip, invoice search and a run list that
 * says which run needs attention (specs/006).
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KjoringFanerIT extends BillingFixture {

    @Autowired MockMvc mvc;

    /** Three known customers with usage (3, 2 and 1 × 10.00), plus {@code ukjente} orgnrs without a customer. */
    private Fakturakjoring kjoring(int ukjente) {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "10.0000");
        Long imp = nyImport(JAN);
        String[][] kunder = {{"100000001", "Alfa kommune", "3"}, {"100000002", "Beta etat", "2"}, {"100000003", "Gamma fylke", "1"}};
        for (int i = 0; i < kunder.length; i++) {
            Long k = kunde(kunder[i][0], kunder[i][1], "AKTIV", null).id();
            regel(k, "KN" + (i + 1), null, null, null);
            bruk(imp, JAN, kunder[i][0], "melding", Bruksdatatype.BRUKSVOLUM, kunder[i][2], null);
        }
        for (int i = 0; i < ukjente; i++) {
            bruk(imp, JAN, "90000000" + i, "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        }
        return tjeneste.generer(JAN);
    }

    private String hent(String url, Object... args) throws Exception {
        return mvc.perform(get(url, args)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String faner(String html) {
        int start = html.indexOf("class=\"faner");
        return html.substring(start, html.indexOf("</nav>", start));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/kontroller", "/fakturaer", "/filer", "/historikk"})
    void hverFaneHarEgenAdresseOgErMarkert(String fane) throws Exception {
        var kj = kjoring(0);
        String faner = faner(hent("/kjoringer/{id}" + fane, kj.id()));
        String gjeldende = "href=\"/kjoringer/" + kj.id() + fane + "\" aria-current=\"page\"";
        assertThat(faner).contains(gjeldende);
        assertThat(faner.split("aria-current=\"page\"", -1)).as("exactly one current tab").hasSize(2);
    }

    @Test
    void kontrollerFanenViserAntallBlokkerendeFunn() throws Exception {
        var kj = kjoring(3);
        String faner = faner(hent("/kjoringer/{id}", kj.id()));
        assertThat(faner).contains("class=\"ds-badge\" data-color=\"danger\"", "data-count=\"3\"", "(3 blokkerende funn)");
        assertThat(faner).contains("<span class=\"fane-antall\">3</span>");
    }

    @Test
    void gammelAvstemmingsadresseLederTilKontrollerFanen() throws Exception {
        var kj = kjoring(0);
        mvc.perform(get("/kjoringer/{id}/avstemming", kj.id()))
                .andExpect(redirectedUrl("/kjoringer/" + kj.id() + "/kontroller#bruksdata"));
        assertThat(hent("/kjoringer/{id}/kontroller", kj.id())).contains("id=\"bruksdata\"", "Per produkt og type");
    }

    @Test
    void fremdriftViserHvemSomGjordeHvaOgNesteSteg() throws Exception {
        var kj = kjoring(0);
        String html = hent("/kjoringer/{id}", kj.id());
        String strip = html.substring(html.indexOf("class=\"fremdrift\""), html.indexOf("</ol>", html.indexOf("class=\"fremdrift\"")));
        assertThat(strip).contains("class=\"ferdig\"", "Generert", "class=\"naa\" aria-current=\"step\"",
                "Kontrollert og godkjent");
        assertThat(html.indexOf("class=\"fremdrift\"")).isLessThan(html.indexOf("class=\"neste-steg"));
    }

    @Test
    void historikkenViserKjoringensHendelserNyesteForst() throws Exception {
        var kj = kjoring(0);
        tjeneste.godkjenn(kj.id());
        String html = hent("/kjoringer/{id}/historikk", kj.id());
        assertThat(html).contains(">GODKJENTE<", ">GENERERTE<");
        assertThat(html.indexOf(">GODKJENTE<")).isLessThan(html.indexOf(">GENERERTE<"));
    }

    @Test
    void fakturaerKanSokesOgSorteres() throws Exception {
        var kj = kjoring(0);
        String sok = hent("/kjoringer/{id}/fakturaer?q=100000002", kj.id());
        assertThat(sok).contains("1 av 3 fakturaer treffer «100000002»", "Beta etat").doesNotContain("Alfa kommune");

        String sortert = hent("/kjoringer/{id}/fakturaer?sorter=belop", kj.id());
        assertThat(sortert.indexOf("Alfa kommune")).as("largest first").isLessThan(sortert.indexOf("Gamma fylke"));
        assertThat(sortert).contains("aria-sort=\"descending\"");

        String ingen = hent("/kjoringer/{id}/fakturaer?q=finnes-ikke", kj.id());
        assertThat(ingen).contains("Ingen fakturaer treffer søket.");
    }

    @Test
    void fakturaerPagineres50PerSideOgVirkerUtenJs() throws Exception {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long imp = nyImport(JAN);
        for (int i = 0; i < 55; i++) {
            String orgnr = String.valueOf(100000100 + i);
            Long k = kunde(orgnr, "Kunde " + i, "AKTIV", null).id();
            regel(k, "KN" + i, null, null, null);
            bruk(imp, JAN, orgnr, "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        }
        var kj = tjeneste.generer(JAN);

        String side1 = hent("/kjoringer/{id}/fakturaer", kj.id());
        assertThat(side1).contains("data-current=\"1\"", "data-total=\"2\"", "aria-current=\"true\"",
                "href=\"/kjoringer/" + kj.id() + "/fakturaer?q=&amp;sorter=ordre&amp;side=2\"");
        assertThat(side1.split("/faktura/", -1)).as("50 invoice rows on page 1").hasSize(51);

        String side2 = hent("/kjoringer/{id}/fakturaer?side=2", kj.id());
        assertThat(side2.split("/faktura/", -1)).as("5 invoice rows on page 2").hasSize(6);
    }

    @Test
    void listenViserStatusAntallSumOgFunn() throws Exception {
        kjoring(2);
        String html = hent("/kjoringer");
        assertThat(html).contains("Venter på godkjenning", "2 blokkerende", "60,00");
    }
}
