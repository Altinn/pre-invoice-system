package no.digdir.forsystem.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Export configuration (docs/04 Phase 4). The responsible persons on the LG04 header come from here
 * rather than being hardcoded (a defect in the old system). Values are placeholders until confirmed
 * with Økonomi — TODO(OQ-2 owner) — and are set per environment via {@code forsystem.eksport.*}.
 */
@ConfigurationProperties(prefix = "forsystem.eksport")
public record EksportKonfig(
        String ansvarlig1,
        String ansvarlig2,
        String fakturaUrlBase) {

    public EksportKonfig {
        if (ansvarlig1 == null || ansvarlig1.isBlank()) {
            ansvarlig1 = "TODO Ansvarlig"; // TODO(OQ-2): confirm responsible person with Økonomi
        }
        if (ansvarlig2 == null || ansvarlig2.isBlank()) {
            ansvarlig2 = ansvarlig1;
        }
        if (fakturaUrlBase == null || fakturaUrlBase.isBlank()) {
            fakturaUrlBase = "https://faktura.digdir.no/detaljer";
        }
    }
}
