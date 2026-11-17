package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.billing.Kontrollkode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** The learning center answers for every signed-in user and shows what the content promises (specs/016). */
@AutoConfigureMockMvc
@WithMockUser(roles = "LESER")
class HjelpIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired Hjelpebibliotek bibliotek;

    /** Thymeleaf escapes only & < > " ' — unlike HtmlUtils, it leaves «», æøå alone. */
    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String hent(String sti) throws Exception {
        return mvc.perform(get(sti)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void startsidenViserFireKortOgJegVil() throws Exception {
        String html = hent("/hjelp");
        assertThat(html).contains("Læringssenter", "Ny her? Start her", "Månedsrutinen steg for steg", "Noe gikk galt",
                "Oppslag", "Jeg vil …");
        bibliotek.jegVil().forEach(j -> assertThat(html).contains("href=\"/hjelp/" + j.artikkelId() + "\""));
        // The menu is there, but no article is current.
        assertThat(html).contains("aria-label=\"Læringssenter\"").doesNotContain("aria-current=\"page\" href=\"/hjelp/");
        // Each card leads to a real article, not the start page or a 404.
        for (String kort : new String[] {"kom-i-gang", "manedsrutinen", "nar-noe-gar-galt", "oppslag/kontrollfunn"}) {
            String mal = kort.contains("/") ? "/hjelp/" + kort : bibliotek.forsteI(kort).orElseThrow().url();
            assertThat(html).contains("href=\"" + mal + "\"");
            hent(mal);
        }
        assertThat(currentLenker(html)).as("only the Hjelp link and the breadcrumb are current").allMatch("/hjelp"::equals);
    }

    @Test
    void hverArtikkelGir200MedTittelSammendragOgMarkertMenypunkt() throws Exception {
        for (Artikkel a : bibliotek.alle()) {
            String html = hent(a.url());
            assertThat(html).as(a.id()).contains("<h1").contains(esc(a.tittel()))
                    .contains(esc(a.sammendrag())).contains("Sist oppdatert");
            assertThat(currentLenker(html)).as("%s: current in the menu", a.id()).contains(a.url());
            // Every menu item shows its article's title as link text (not an empty link).
            assertThat(html).as("%s: menu link text", a.id()).contains(">" + esc(a.tittel()) + "</a>");
            assertThat(html).as(a.id()).doesNotContain("<script src=\"http");
        }
    }

    @Test
    void artikkelISpiHarStegOgForrigeNeste() throws Exception {
        String html = hent("/hjelp/manedsrutinen/3-generer");
        assertThat(html).contains("Steg 3 av 6", "aria-current=\"step\"", "Forrige: ", "Neste: ");
        assertThat(html).doesNotContain("href=\"/hjelp/manedsrutinen/1-sjekk-registrene\"></a>");
        Artikkel a = bibliotek.artikkel("manedsrutinen", "3-generer").orElseThrow();
        assertThat(html).contains(esc(a.steg().forrige().tittel()))
                .contains(esc(a.steg().neste().tittel()));
    }

    @Test
    void artikkelMedRolleViserKravetOgKnappTilAppen() throws Exception {
        Artikkel a = bibliotek.alle().stream().filter(x -> x.rolle() != null && x.apneIAppen() != null).findFirst().orElseThrow();
        String html = hent(a.url());
        assertThat(html).contains("Krever rollen " + a.rolle(), "Gå til i appen", "href=\"" + a.apneIAppen() + "\"");
    }

    @Test
    void ukjentArtikkelGir404MedLenkeTilLaeringssenteret() throws Exception {
        mvc.perform(get("/hjelp/finnes/ikke")).andExpect(status().isNotFound());
        // MockMvc does not forward to the error page; render it the way the container does after sendError(404).
        mvc.perform(get("/error").accept(org.springframework.http.MediaType.TEXT_HTML).requestAttr("jakarta.servlet.error.status_code", 404)
                        .requestAttr("jakarta.servlet.error.request_uri", "/hjelp/finnes/ikke"))
                .andExpect(content().string(containsString("Fant ikke siden")))
                .andExpect(content().string(containsString("href=\"/hjelp\"")))
                .andExpect(content().string(not(containsString("Noe gikk galt"))));
    }

    @Test
    void kontrollfunnSidenViserHverKodeMedAlvorlighetDefinisjonOgLosning() throws Exception {
        String html = hent("/hjelp/oppslag/kontrollfunn");
        for (Kontrollkode k : Kontrollkode.values()) {
            assertThat(html).contains("id=\"" + k.name() + "\"", esc(k.definisjon()));
            assertThat(html).as("«Slik løser du det» for %s", k).contains(bibliotek.kontrollfunn().get(k).strip());
        }
        assertThat(html).contains("Blokkerende", "Advarsel", "Slik løser du det");
    }

    @Test
    void kontrollerFanenLenkerTilKontrollfunnReferansen() throws Exception {
        // The Kontroller tab needs a run; the link text and target are static in the template.
        String mal = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/templates/kjoring/kontroller.html"));
        assertThat(mal).contains("Les mer i Læringssenteret").contains("/hjelp/oppslag/kontrollfunn");
    }

    @Test
    void ingenArtikkelSlipperInnScriptFraInnhold() throws Exception {
        for (Artikkel a : bibliotek.alle()) {
            mvc.perform(get(a.url())).andExpect(content().string(not(containsString("<script>"))));
        }
    }

    @Test
    void sokGirTreffMedTittelOgSammendrag() throws Exception {
        String html = hent("/hjelp/sok?q=kontering");
        assertThat(html).contains("treff for «kontering»").contains("href=\"/hjelp/slik-gjor-du/legg-inn-kontering\"");
    }

    @Test
    void sokUtenTreffPekerTilStartsidenOgNarNoeGarGalt() throws Exception {
        String html = hent("/hjelp/sok?q=qqqqxyzzy");
        assertThat(html).contains("Ingen treff for «qqqqxyzzy».").contains("href=\"/hjelp\"")
                .contains("href=\"/hjelp/nar-noe-gar-galt/oversikt\"");
    }

    @Test
    void tomtSokGarTilStartsiden() throws Exception {
        mvc.perform(get("/hjelp/sok?q=")).andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/hjelp"));
        mvc.perform(get("/hjelp/sok")).andExpect(status().is3xxRedirection());
    }

    @Test
    void sokefeltetErMedPaaSidene() throws Exception {
        assertThat(hent("/hjelp")).contains("role=\"search\"", "action=\"/hjelp/sok\"");
    }

    /** Hrefs of anchors marked aria-current="page" (the Hjelp link plus the current menu item). */
    static java.util.List<String> currentLenker(String html) {
        java.util.List<String> ut = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("<a\\b[^>]*>").matcher(html);
        while (m.find()) {
            String tag = m.group();
            if (tag.contains("aria-current=\"page\"")) {
                Matcher h = Pattern.compile("href=\"([^\"]+)\"").matcher(tag);
                if (h.find()) {
                    ut.add(h.group(1));
                }
            }
        }
        return ut;
    }
}
