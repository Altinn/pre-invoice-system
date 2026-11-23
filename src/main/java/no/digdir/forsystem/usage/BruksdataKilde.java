package no.digdir.forsystem.usage;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One source group of an imported usage row (specs/017 FR-008, docs/03 V16): the day rows of a DWH source
 * table and resource that were summed into the row of the same (import, orgnr, product, type). Insert-only;
 * {@code antall} is set for volume types and {@code belop} for cost types. {@code regel} holds the distinct
 * {@code regel} texts verbatim.
 */
@Table("bruksdata_kilde")
public record BruksdataKilde(
        @Id Long id,
        Long importId,
        String organisasjonsnummer,
        Long produktId,
        String type,
        String kildetabell,
        String ressurs,
        Integer antallDagsrader,
        BigDecimal antall,
        BigDecimal belop,
        String[] regel) {
}
