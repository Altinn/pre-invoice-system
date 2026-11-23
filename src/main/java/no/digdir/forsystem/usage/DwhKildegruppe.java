package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Grouped source summary of a usage row (specs/017 FR-008): the day rows that contributed to one
 * (orgnr, product, type), grouped by the DWH's source table and resource. The groups of a usage row sum
 * to its figure. {@code regler} holds the distinct {@code regel} texts verbatim.
 */
public record DwhKildegruppe(
        String organisasjonsnummer,
        String produktkode,
        String type,
        String kildetabell,
        String ressurs,
        int antallDagsrader,
        BigDecimal sum,
        List<String> regler) {
}
