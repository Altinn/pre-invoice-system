package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import no.digdir.forsystem.web.hjelp.Artikkel.Blokk;
import no.digdir.forsystem.web.hjelp.Artikkel.Html;
import no.digdir.forsystem.web.hjelp.HjelpRenderer.Resultat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HjelpRendererTest {

    private final HjelpRenderer renderer = new HjelpRenderer();

    private Resultat render(String body) {
        return renderer.render("---\ntittel: T\nsammendrag: S\noppdatert: 2026-11-01\n---\n" + body,
                "manedsrutinen", "1-sjekk");
    }

    private String html(Resultat r) {
        StringBuilder sb = new StringBuilder();
        r.deler().forEach(d -> {
            if (d instanceof Html h) {
                sb.append(h.html());
            }
        });
        return sb.toString();
    }

    @ParameterizedTest
    @CsvSource({"TIP, info, Tips", "IMPORTANT, warning, Viktig", "WARNING, danger, Advarsel"})
    void varselBlirDsAlertMedNorskMerkelapp(String type, String farge, String merkelapp) {
        String html = html(render("> [!" + type + "]\n> Husk dette.\n"));
        assertThat(html).contains("class=\"ds-alert bred\"").contains("data-color=\"" + farge + "\"")
                .contains("<strong>" + merkelapp + "</strong>").contains("Husk dette.");
        assertThat(html).doesNotContain("markdown-alert");
    }

    @Test
    void knappeNavnBlirSpanKnapp() {
        assertThat(html(render("Trykk [Generer](knapp:) nå."))).contains("<span class=\"knapp\">Generer</span>");
    }

    @Test
    void ravHtmlBlirEscapet() {
        String html = html(render("Tekst <script>alert(1)</script> og <b>fet</b>.\n\n<div>blokk</div>\n"));
        assertThat(html).doesNotContain("<script").doesNotContain("<b>").doesNotContain("<div>blokk");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void relativMdLenkeSkrivesOmTilAppenSinSti() {
        Resultat r = render("Se [steget](../manedsrutinen/3-generer.md#hvis-noe-gar-galt).");
        assertThat(html(r)).contains("href=\"/hjelp/manedsrutinen/3-generer#hvis-noe-gar-galt\"").contains("class=\"ds-link\"");
        assertThat(r.artikkelLenker()).containsExactly(
                new HjelpRenderer.ArtikkelLenke("manedsrutinen/3-generer", "hvis-noe-gar-galt"));
    }

    @Test
    void lenkeTilSammeMappeOgAppSti() {
        Resultat r = render("[a](2-hent.md) og [b](/kjoringer?x=1)");
        assertThat(r.artikkelLenker()).extracting(HjelpRenderer.ArtikkelLenke::id).containsExactly("manedsrutinen/2-hent");
        assertThat(r.appLenker()).containsExactly("/kjoringer");
    }

    @Test
    void eksterneLenkerOgBilderErProblemer() {
        Resultat r = render("[a](https://example.com) [b](mailto:a@b.no) ![c](bilde.png) [d](foo)");
        assertThat(r.problemer()).hasSize(4);
    }

    @Test
    void h2FarIdOgHavnerIInnhold() {
        Resultat r = render("## Før du starter\n\ntekst\n\n### Under\n\n## Gjør slik\n");
        assertThat(html(r)).contains("id=\"før-du-starter\"").contains("class=\"ds-heading\"").contains("data-size=\"sm\"")
                .contains("data-size=\"xs\"");
        assertThat(r.innhold()).extracting(Artikkel.Overskrift::tekst).containsExactly("Før du starter", "Gjør slik");
        assertThat(r.ankre()).contains("før-du-starter", "gjør-slik");
    }

    @Test
    void blokkPlassholderBlirEnBlokk() {
        Resultat r = render("Før.\n\n{{kontrollkoder}}\n\nEtter.\n");
        assertThat(r.deler()).hasSize(3);
        assertThat(r.deler().get(1)).isEqualTo(new Blokk("kontrollkoder"));
        assertThat(r.blokker()).containsExactly("kontrollkoder");
    }

    @Test
    void tabellFarDsTable() {
        String html = html(render("| A | B |\n|---|---|\n| 1 | 2 |\n"));
        assertThat(html).contains("<table class=\"ds-table\" data-size=\"sm\"");
    }

    @Test
    void tomKnappErProblem() {
        assertThat(render("[](knapp:)").problemer()).anyMatch(p -> p.contains("knapp:"));
    }

    @Test
    void overskriftPaNivaaEnErProblem() {
        assertThat(render("# Tittel\n").problemer()).anyMatch(p -> p.contains("nivå 1"));
    }

    @Test
    void frontMatterLesesOgTekstTrekkesUt() {
        Resultat r = render("Dette er **viktig** tekst.\n");
        assertThat(r.frontMatter()).containsEntry("tittel", List.of("T"));
        assertThat(r.tekst()).isEqualTo("Dette er viktig tekst.");
    }
}
