package no.digdir.forsystem.billing;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A control finding for a run (docs/03 V4). {@code fakturaId} is null for run-level findings such
 * as usage whose orgnr has no customer. BLOKKERENDE findings prevent approval.
 */
@Table("kontrollfunn")
public record Kontrollfunn(
        @Id Long id,
        Long kjoringId,
        Long fakturaId,
        String alvorlighet,
        String kode,
        String melding) {
}
