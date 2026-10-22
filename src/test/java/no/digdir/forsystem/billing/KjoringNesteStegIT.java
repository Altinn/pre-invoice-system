package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The run page leads with the next step for its status, groups findings per code and gathers the
 * files in one section (specs/005 FR-001..009).
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KjoringNesteStegIT extends BillingFixture {

    @Autowired MockMvc mvc;

    /** One known customer with usage; {@code ukjente} extra orgnrs with usage but no customer. */
    private Fakturakjoring kjoring(int ukjente) {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        for (int i = 0; i < ukjente; i++) {
            bruk(imp, JAN, "90000000" + i, "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        }
        return tjeneste.generer(JAN);
    }

    private String side(Long id) throws Exception {
        return mvc.perform(get("/kjoringer/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** The panel comes before the summary, and its text before the next section. */
    private static String panel(String html) {
        int start = html.indexOf("class=\"neste-steg");
        int slutt = html.indexOf(">Sammendrag<");
        assertThat(start).as("panel present").isPositive();
        assertThat(start).as("panel before summary").isLessThan(slutt);
        return html.substring(start, slutt);
    }

    private static String godkjennKnapp(String panel) {
        int i = panel.indexOf("data-bekreft=\"bekreft-godkjenn\"");
        return panel.substring(panel.lastIndexOf("<button", i), panel.indexOf(">", i) + 1);
    }

    @Test
    void generertUtenFunnKanGodkjennes() throws Exception {
        var html = side(kjoring(0).id());
        var panel = panel(html);
        assertThat(panel).contains("Neste steg: kontroller og godkjenn", "Ingen blokkerende funn", "Ingen advarsler",
                "All bruksdata er med i fakturagrunnlaget");
        assertThat(godkjennKnapp(panel)).doesNotContain("disabled");
        assertThat(html).contains("<h1 class=\"ds-heading\" data-size=\"lg\">Kjøring januar 2027</h1>");
    }

    @Test
    void blokkerendeFunnGrupperesOgLaaserGodkjenning() throws Exception {
        var kj = kjoring(3);
        var html = side(kj.id());
        var panel = panel(html);
        assertThat(panel).contains("3 blokkerende funn må løses før godkjenning.");
        assertThat(godkjennKnapp(panel)).contains("disabled");

        // Grouped findings live on the Kontroller tab since specs/006.
        String kontroller = mvc.perform(get("/kjoringer/{id}/kontroller", kj.id())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String funn = kontroller.substring(kontroller.indexOf("id=\"kontrollfunn\""), kontroller.indexOf("id=\"bruksdata\""));
        assertThat(funn.split("<code>MANGLER_KUNDE</code>", -1)).as("one row for the code").hasSize(2);
        assertThat(funn).contains("<td class=\"hoyre\">3</td>", "Vis alle 3 MANGLER_KUNDE");
    }

    @Test
    void godkjentTilbyrEksport() throws Exception {
        var kj = kjoring(0);
        tjeneste.godkjenn(kj.id());
        var panel = panel(side(kj.id()));
        assertThat(panel).contains("Neste steg: eksporter til regnskap", "Godkjent av",
                "data-bekreft=\"bekreft-eksporter\"", "data-bekreft=\"bekreft-forkast\"");
    }

    @Test
    void forkastetTilbyrAaGenererePeriodenPaaNytt() throws Exception {
        var kj = kjoring(0);
        tjeneste.forkast(kj.id());
        var panel = panel(side(kj.id()));
        assertThat(panel).contains("Kjøringen er forkastet", "name=\"periode\" value=\"2027-01\"",
                "Generer januar 2027 på nytt");
    }

    @Test
    void filerErSamletEttSted() throws Exception {
        var kj = kjoring(0);
        // Files live on the Filer tab since specs/006.
        String filer = mvc.perform(get("/kjoringer/{id}/filer", kj.id())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        filer = filer.substring(filer.indexOf("id=\"filer\""));
        assertThat(filer).contains("fakturagrunnlag.xlsx", "fakturagrunnlag.csv", "Avstemming bruksdata");
    }

    @Test
    void listenViserMaanedenNorsk() throws Exception {
        kjoring(0);
        String html = mvc.perform(get("/kjoringer")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("<td class=\"periode\">januar 2027</td>").doesNotContain(">2027-01-01<");
    }
}
