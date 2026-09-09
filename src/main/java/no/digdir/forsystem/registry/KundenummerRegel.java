package no.digdir.forsystem.registry;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps a customer to a Unit4 {@code kundenummer} (docs/03 V2). Models the non-1:1 orgnr↔kundenummer
 * cases: several kundenummer per orgnr (distinct {@code servicekode} or {@code produktId}), and a
 * shared kundenummer across orgs distinguished by {@code tilleggstekst}. Invoices group by
 * kundenummer, so two servicekode rows yield two invoices.
 */
@Table("kundenummer_regel")
public record KundenummerRegel(
        @Id Long id,
        Long kundeId,
        String kundenummer,
        Long produktId,
        String servicekode,
        String tilleggstekst) {
}
