package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One usage import batch (docs/03 V3). {@code periode} is the first day of the billed month. A
 * datavarehus import also records where its raw response is archived, its SHA-256 and fetch time.
 */
@Table("bruksdata_import")
public record BruksdataImport(
        @Id Long id,
        String filnavn,
        LocalDate periode,
        String kilde,
        String status,
        Integer antallRader,
        String lastetAv,
        OffsetDateTime lastetAt,
        String raadataUrl,
        String raadataSha256,
        OffsetDateTime hentetAt) {

    /** A CSV import has no archived raw payload. */
    public static BruksdataImport csv(String filnavn, LocalDate periode, String status, Integer antallRader,
                                      String lastetAv, OffsetDateTime lastetAt) {
        return new BruksdataImport(null, filnavn, periode, Kilde.CSV.name(), status, antallRader,
                lastetAv, lastetAt, null, null, null);
    }

    public BruksdataImport medStatus(String nyStatus) {
        return new BruksdataImport(id, filnavn, periode, kilde, nyStatus, antallRader, lastetAv, lastetAt,
                raadataUrl, raadataSha256, hentetAt);
    }

    public boolean harRaadata() {
        return raadataUrl != null;
    }
}
