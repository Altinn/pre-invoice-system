package no.digdir.forsystem.billing;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A file produced by exporting a run (docs/03 V4): LG04, PDF (zip), CSV or XLSX. {@code blobUrl} is
 * a local path in dev and a blob URL in prod; {@code sha256} lets a later delivery step verify
 * integrity (docs/06 §3).
 */
@Table("eksportfil")
public record Eksportfil(
        @Id Long id,
        Long kjoringId,
        String type,
        String filnavn,
        String blobUrl,
        String sha256,
        String opprettetAv,
        OffsetDateTime opprettetAt) {
}
