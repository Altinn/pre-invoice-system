package no.digdir.forsystem.billing;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The stored proof that an exported LG04 file matched its run (specs/002 D6): orders and øre sum read
 * back from the file, the same figures computed from the run, and the checksum of the bytes that were
 * checked. Only matching reconciliations exist — a mismatch stops the export before anything is saved.
 */
@Table("lg04_avstemming")
public record Lg04Avstemming(
        @Id Long id,
        Long kjoringId,
        Long eksportfilId,
        int antallOrdrerFil,
        long sumOreFil,
        int antallOrdrerKjoring,
        long sumOreKjoring,
        int antallFakturaer,
        String sha256,
        String avstemtAv,
        OffsetDateTime avstemtAt) {
}
