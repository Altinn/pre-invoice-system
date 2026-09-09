package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One usage measurement (docs/03 V3). Natural key (periode, orgnr, produkt, type) is unique;
 * {@code antall} is set for BRUKSVOLUM, {@code belop} for the pass-through cost types.
 */
@Table("bruksdata")
public record Bruksdata(
        @Id Long id,
        Long importId,
        LocalDate periode,
        String organisasjonsnummer,
        Long produktId,
        String type,
        BigDecimal antall,
        BigDecimal belop) {
}
