package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One invoice within a run (docs/03 V4). Since specs/018 a run has one invoice per organisation, with the
 * customer values its usage import delivered; runs made earlier may have several per organisation (one per
 * kundenummer). {@code organisasjonsnummer}, {@code kundenummer}, {@code kundenavn}, {@code fakturamottakerOrgnr}
 * and {@code tilleggstekst} are snapshotted at generation so a later import never rewrites history;
 * {@code tilleggstekst} is only set on invoices from before specs/018. {@code ordreNr} is the order id within the
 * LG04 file.
 */
@Table("faktura")
public record Faktura(
        @Id Long id,
        Long kjoringId,
        String organisasjonsnummer,
        UUID fakturaUuid,
        Integer ordreNr,
        String kundenummer,
        String kundenavn,
        String tilleggstekst,
        String fakturamottakerOrgnr,
        BigDecimal sumBelop) {
}
