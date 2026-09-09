package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One invoice within a run (docs/03 V4). Invoices group by kundenummer, not by customer:
 * a customer with two kundenummer yields two invoices. {@code kundenummer},
 * {@code fakturamottakerOrgnr} and {@code tilleggstekst} are snapshotted at generation so later
 * registry edits never rewrite history. {@code ordreNr} is the order id within the LG04 file.
 */
@Table("faktura")
public record Faktura(
        @Id Long id,
        Long kjoringId,
        Long kundeId,
        UUID fakturaUuid,
        Integer ordreNr,
        String kundenummer,
        String tilleggstekst,
        String fakturamottakerOrgnr,
        BigDecimal sumBelop) {
}
