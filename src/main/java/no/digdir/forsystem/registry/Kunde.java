package no.digdir.forsystem.registry;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A customer (tjenesteeier) in the temporary registry (docs/03 V2), used until CRM takes over
 * ({@code kilde} flips to 'CRM'). {@code fakturamottakerOrgnr} supports a deviating invoice
 * recipient (a different legal entity — OQ-7). References and kundenummer rules are separate
 * aggregates ({@link KundeReferanse}, {@link KundenummerRegel}).
 */
@Table("kunde")
public record Kunde(
        @Id Long id,
        String organisasjonsnummer,
        String virksomhetsnavn,
        String fakturamottakerOrgnr,
        String avtalestatus,
        String kilde,
        String oppdatertAv,
        OffsetDateTime oppdatertAt) {

    public Avtalestatus avtalestatusEnum() {
        return Avtalestatus.valueOf(avtalestatus);
    }
}
