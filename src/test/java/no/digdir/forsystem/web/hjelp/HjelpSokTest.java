package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HjelpSokTest {

    private static final Hjelpebibliotek BIBLIOTEK = new Hjelpebibliotek();
    private final Hjelpesok sok = new Hjelpesok(BIBLIOTEK);

    @Test
    void normaliseringBeholderAeOgAaMenFjernerAksenter() {
        assertThat(Hjelpesok.normaliser("Kjøring ÆØÅ café")).isEqualTo("kjøring æøå cafe");
        assertThat(Hjelpesok.normaliser("Går")).isEqualTo("går");
    }

    @Test
    void tomSporringGirIngenTreff() {
        assertThat(sok.sok("  ")).isEmpty();
        assertThat(sok.sok(null)).isEmpty();
    }

    @Test
    void altOrdMaTreffe() {
        assertThat(sok.sok("kontering zzzzzzzz")).isEmpty();
        assertThat(sok.sok("kontering")).isNotEmpty();
    }

    @Test
    void ingenMerEnnTjueTreff() {
        assertThat(sok.sok("a").size()).isLessThanOrEqualTo(Hjelpesok.MAKS_TREFF);
    }

    @Test
    void tittelSlarSammendragSlarBrodtekst() {
        // «terskelverdi» is in the title of one article and only in the text of others.
        var treff = sok.sok("terskelverdi");
        assertThat(treff).hasSizeGreaterThan(1);
        for (int i = 1; i < treff.size(); i++) {
            assertThat(treff.get(i - 1).poeng()).isGreaterThanOrEqualTo(treff.get(i).poeng());
        }
        assertThat(treff.getFirst().artikkel().tittel().toLowerCase()).contains("terskelverdi");
    }

    /** SC-006: ten common terms each find the expected article first. */
    @ParameterizedTest
    @CsvSource({
            "terskelverdi, forsta-systemet/terskelverdi",
            "kontering, slik-gjor-du/legg-inn-kontering",
            "LG04, slik-gjor-du/er-lg04-lastet-ned",
            "forkast, slik-gjor-du/forkast-og-generer-pa-nytt",
            "fakturareferanse, slik-gjor-du/legg-til-fakturareferanse",
            "kildenavn, slik-gjor-du/koble-kildenavn",
            "prisversjon, slik-gjor-du/lag-prisversjon",
            "advarsel, oppslag/kontrollfunn",
            "CSV, oppslag/csv-format",
            "korrigering, forsta-systemet/korrigering-og-avregning",
    })
    void vanligeBegrepFinnerForventetArtikkelForst(String sporring, String forventet) {
        var treff = sok.sok(sporring);
        assertThat(treff).as(sporring).isNotEmpty();
        assertThat(treff.getFirst().artikkel().id()).as("første treff for «%s»", sporring).isEqualTo(forventet);
    }

    @Test
    void ingenTreffForNonsens() {
        assertThat(sok.sok("qqqqxyzzy")).isEmpty();
    }
}
