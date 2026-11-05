package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;

/** Defaults and validation of a run's invoice texts against the LG04 fields (specs/009 FR-002, FR-005). */
class FakturateksterTest {

    private static final Fakturatekster GYLDIG = Fakturatekster.standard(Periode.av(2027, 1));

    @Test
    void defaultsAreOkonomisTextsWithTheRunsPeriod() {
        assertThat(Fakturatekster.standard(Periode.av(2027, 3))).isEqualTo(new Fakturatekster(
                "Bruk av Altinn mars 2027",
                "Se samarbeid.digdir.no for priser og innsyn i egen statistikk",
                "Servicedesk@altinn.no"));
    }

    @Test
    void defaultsAreValidAndWithinThePrintedSuggestion() {
        assertThatCode(GYLDIG::valider).doesNotThrowAnyException();
        assertThat(GYLDIG.topptekstOverTrykkgrense()).isFalse();
        assertThat(GYLDIG.bunntekstOverTrykkgrense()).isFalse();
    }

    @Test
    void normalisingTrimsEachText() {
        assertThat(new Fakturatekster("  a ", "\tb", "c  ").normalisert()).isEqualTo(new Fakturatekster("a", "b", "c"));
    }

    @Test
    void lengthsUpToTheLg04FieldsAreAccepted() {
        assertThatCode(() -> new Fakturatekster("x".repeat(120), "y".repeat(120), "z".repeat(25)).valider())
                .doesNotThrowAnyException();
    }

    @Test
    void oneCharacterOverAnLg04FieldIsRefused() {
        assertThatThrownBy(() -> new Fakturatekster("x".repeat(121), "b", "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessage("Topptekst er 121 tegn, men LG04-feltet har plass til 120");
        assertThatThrownBy(() -> new Fakturatekster("a", "y".repeat(121), "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Bunntekst").hasMessageContaining("120");
        assertThatThrownBy(() -> new Fakturatekster("a", "b", "z".repeat(26)).valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Vår referanse").hasMessageContaining("25");
    }

    @Test
    void emptyTextsAreRefused() {
        assertThatThrownBy(() -> new Fakturatekster("   ", "b", "c").normalisert().valider())
                .isInstanceOf(Regelbrudd.class).hasMessage("Topptekst kan ikke være tom");
        assertThatThrownBy(() -> new Fakturatekster("a", null, "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessage("Bunntekst kan ikke være tom");
    }

    @Test
    void lineBreaksAndTabsAreRefused() {
        assertThatThrownBy(() -> new Fakturatekster("a\nb", "b", "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("linjeskift");
        assertThatThrownBy(() -> new Fakturatekster("a", "b", "c\td").valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Vår referanse");
    }

    @Test
    void charactersWindows1252CannotHoldAreRefusedByName() {
        assertThatThrownBy(() -> new Fakturatekster("Bruk av Altinn 🚀", "b", "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("«🚀»").hasMessageContaining("Windows-1252");
        assertThatThrownBy(() -> new Fakturatekster("a", "Ω", "c").valider())
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Bunntekst");
    }

    @Test
    void norwegianLettersAndTheEuroSignAreAccepted() {
        assertThatCode(() -> new Fakturatekster("Æ Ø Å æ ø å", "Pris i € og kr", "Servicedesk@altinn.no").valider())
                .doesNotThrowAnyException();
    }

    @Test
    void textsAboveThePrintedSuggestionAreFlaggedNotRefused() {
        var lang = new Fakturatekster("x".repeat(91), "y".repeat(66), "c");
        assertThatCode(lang::valider).doesNotThrowAnyException();
        assertThat(lang.topptekstOverTrykkgrense()).isTrue();
        assertThat(lang.bunntekstOverTrykkgrense()).isTrue();
    }

    @Test
    void changesListOnlyTheFieldsThatDiffer() {
        var ny = new Fakturatekster(GYLDIG.topptekst(), "Ny", GYLDIG.varReferanse());
        assertThat(ny.endringerFra(GYLDIG)).containsOnlyKeys("bunntekst");
        assertThat(ny.endringerFra(GYLDIG).get("bunntekst"))
                .containsEntry("fra", GYLDIG.bunntekst()).containsEntry("til", "Ny");
        assertThat(GYLDIG.endringerFra(GYLDIG)).isEmpty();
    }
}
