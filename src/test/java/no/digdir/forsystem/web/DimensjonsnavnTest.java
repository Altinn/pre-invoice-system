package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Dimension labels come from configuration only and fall back to the bare name (specs/003 D4, OQ-2). */
class DimensjonsnavnTest {

    @Test
    void bareLabelsWhenNothingIsConfigured() {
        var d = new Dimensjonsnavn(null, "", "  ");
        assertThat(d.etikett(1)).isEqualTo("Dim 1");
        assertThat(d.etikett(2)).isEqualTo("Dim 2");
        assertThat(d.etikett(4)).isEqualTo("Dim 4");
    }

    @Test
    void configuredNameIsShownInParentheses() {
        assertThat(new Dimensjonsnavn("koststed", null, null).etikett(1)).isEqualTo("Dim 1 (koststed)");
    }
}
