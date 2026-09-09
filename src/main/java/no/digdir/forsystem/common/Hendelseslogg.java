package no.digdir.forsystem.common;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One audit-trail row (docs/02, docs/03 V5). Immutable — audit rows are appended, never edited.
 * {@code detaljer} holds arbitrary structured context as {@link Jsonb}.
 */
@Table("hendelseslogg")
public record Hendelseslogg(
        @Id Long id,
        OffsetDateTime tidspunkt,
        String bruker,
        String handling,
        String entitet,
        Long entitetId,
        Jsonb detaljer) {
}
