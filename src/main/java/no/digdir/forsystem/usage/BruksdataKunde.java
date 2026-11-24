package no.digdir.forsystem.usage;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The customer values one import delivered for one organisation (specs/018, docs/03 V17). Customer data is
 * mastered in CRM and reaches forsystem through the DWH, so this is what generation puts on the invoice.
 * Insert-only. {@code motstridende} lists values that disagreed between the import's rows; generation
 * blocks such an organisation instead of picking one.
 */
@Table("bruksdata_kunde")
public record BruksdataKunde(
        @Id Long id,
        Long importId,
        String organisasjonsnummer,
        String virksomhetsnavn,
        String kundenummer,
        String fakturareferanse,
        String bestillingsnummer,
        String[] motstridende) {

    public boolean harMotstridende() {
        return motstridende != null && motstridende.length > 0;
    }
}
