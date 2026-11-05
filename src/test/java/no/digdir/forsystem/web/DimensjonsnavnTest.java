package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Dimension labels come from configuration and fall back to the bare name (specs/003 D4, specs/010 FR-004). */
class DimensjonsnavnTest {

    @Test
    void bareLabelsWhenNothingIsConfigured() {
        var d = new Dimensjonsnavn(null, "", "  ");
        assertThat(d.etikett(1)).isEqualTo("Dim1");
        assertThat(d.etikett(2)).isEqualTo("Dim2");
        assertThat(d.etikett(4)).isEqualTo("Dim4");
    }

    @Test
    void configuredNameFollowsTheBareLabel() {
        // Økonomi writes "Dim1 Koststed" — no space in Dim1, no parentheses (specs/010 FR-004).
        assertThat(new Dimensjonsnavn("Koststed", null, null).etikett(1)).isEqualTo("Dim1 Koststed");
    }
}
