package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import no.digdir.forsystem.common.Periode;

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
        Long bruksdataImportId,
        String generertAv,
        OffsetDateTime generertAt,
        String godkjentAv,
        OffsetDateTime godkjentAt,
        String kommentar) {

    /** The billing period as Norwegian month and year, e.g. "januar 2027" (specs/005 FR-009). */
    public String periodeLabel() {
        return new Periode(periode).norskLabel();
    }
}
