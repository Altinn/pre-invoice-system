package no.digdir.forsystem.billing;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One invoice line (docs/03 V4). Traceable to the usage ({@code bruksdataId}) and the price
 * ({@code prisId}) it was computed from. {@code fakturareferanse}/{@code bestillingsnummer}/
 * {@code servicekode} are snapshots at generation. For pass-through cost lines {@code antall},
 * {@code enhetspris} and {@code prisId} are null.
 */
@Table("fakturalinje")
public record Fakturalinje(
        @Id Long id,
        Long fakturaId,
        Long produktId,
        String beskrivelse,
        BigDecimal antall,
        BigDecimal enhetspris,
        BigDecimal belop,
        Long prisId,
        Long bruksdataId,
        String servicekode,
        String fakturareferanse,
        String bestillingsnummer) {
}
