package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An invoicing run for one period (docs/03 V4). Introduced minimally in Phase 2 so usage import can
 * check for an existing non-FORKASTET run; Phase 3 fills in generation, controls and approval.
 */
@Table("fakturakjoring")
public record Fakturakjoring(
        @Id Long id,
        LocalDate periode,
        String status,
        Long prisversjonId,
        String generertAv,
        OffsetDateTime generertAt,
        String godkjentAv,
        OffsetDateTime godkjentAt,
        String kommentar) {
}
