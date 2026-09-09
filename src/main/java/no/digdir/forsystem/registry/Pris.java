package no.digdir.forsystem.registry;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The unit price for one product within one price version (docs/03 V1). Unique per
 * (prisversjon, produkt). Money is {@code numeric(12,4)} → {@link BigDecimal}.
 */
@Table("pris")
public record Pris(
        @Id Long id,
        Long prisversjonId,
        Long produktId,
        BigDecimal enhetspris) {
}
