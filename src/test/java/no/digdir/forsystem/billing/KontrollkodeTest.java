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
                        Kontrollkode.MOTSTRIDENDE_KUNDEDATA, Kontrollkode.INAKTIV_AVTALE, Kontrollkode.MANGLER_PRIS,
                        Kontrollkode.MANGLER_KONTERING);
    }

    @Test
    void retiredCodesKeepTheirDefinitionAndAreNotActive() {
        // specs/018: no longer raised, but findings on older runs still render.
        assertThat(Arrays.stream(Kontrollkode.values()).filter(Kontrollkode::utgatt))
                .containsExactlyInAnyOrder(Kontrollkode.MANGLER_KUNDE, Kontrollkode.INAKTIV_AVTALE,
                        Kontrollkode.AKTIV_KUNDE_UTEN_BRUK);
        assertThat(Kontrollkode.aktive()).doesNotContain(Kontrollkode.MANGLER_KUNDE, Kontrollkode.INAKTIV_AVTALE,
                Kontrollkode.AKTIV_KUNDE_UTEN_BRUK).contains(Kontrollkode.MOTSTRIDENDE_KUNDEDATA);
    }
}
