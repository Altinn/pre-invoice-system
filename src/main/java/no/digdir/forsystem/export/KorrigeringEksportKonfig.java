package no.digdir.forsystem.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hard safety gate for correction export; false in every environment until OQ-6 is approved. */
@ConfigurationProperties(prefix = "forsystem.korrigering")
public record KorrigeringEksportKonfig(boolean eksportEnabled) {
}
