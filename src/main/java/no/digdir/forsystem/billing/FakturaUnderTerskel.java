package no.digdir.forsystem.billing;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A would-be invoice that a run left out because the customer's total for the month was not over the
 * price version's threshold (specs/011). Snapshotted like {@link Faktura}; {@code kundesum} is the
 * customer's total over all its would-be invoices, which is what the threshold is compared with.
 */
@Table("faktura_under_terskel")
public record FakturaUnderTerskel(
        @Id Long id,
        Long kjoringId,
        Long kundeId,
        String organisasjonsnummer,
        String kundenavn,
        String kundenummer,
        String tilleggstekst,
        BigDecimal sumBelop,
        Integer antallLinjer,
        BigDecimal kundesum,
        BigDecimal terskelverdi,
        Long kontrollfunnId) {
}
