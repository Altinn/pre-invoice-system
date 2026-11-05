package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Every control code explains itself on the Kontroller tab (specs/010 FR-008). */
class KontrollkodeTest {

    @Test
    void everyCodeHasASeverityAndADefinition() {
        for (Kontrollkode k : Kontrollkode.values()) {
            assertThat(k.alvorlighet()).as(k.name()).isNotNull();
            assertThat(k.definisjon()).as(k.name()).isNotBlank().endsWith(".");
        }
    }

    @Test
    void severitiesMatchHowGenerationRaisesThem() {
        assertThat(Arrays.stream(Kontrollkode.values()).filter(k -> k.alvorlighet() == Alvorlighet.BLOKKERENDE))
                .containsExactlyInAnyOrder(Kontrollkode.MANGLER_KUNDE, Kontrollkode.MANGLER_KUNDENUMMER,
                        Kontrollkode.INAKTIV_AVTALE, Kontrollkode.MANGLER_PRIS, Kontrollkode.MANGLER_KONTERING);
    }
}
