package no.digdir.forsystem.registry;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An invoice reference for a customer (docs/03 V2). {@code produktId} null = the customer default;
 * a product-specific row overrides it (fakturareferanse can differ per product for one customer).
 */
@Table("kunde_referanse")
public record KundeReferanse(
        @Id Long id,
        Long kundeId,
        Long produktId,
        String fakturareferanse,
        String bestillingsnummer) {
}
