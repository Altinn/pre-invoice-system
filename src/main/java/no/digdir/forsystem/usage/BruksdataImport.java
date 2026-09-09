package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** One usage import batch (docs/03 V3). {@code periode} is the first day of the billed month. */
@Table("bruksdata_import")
public record BruksdataImport(
        @Id Long id,
        String filnavn,
        LocalDate periode,
        String kilde,
        String status,
        Integer antallRader,
        String lastetAv,
        OffsetDateTime lastetAt) {
}
