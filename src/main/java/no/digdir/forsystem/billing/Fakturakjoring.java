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
        String kommentar,
        String type,
        Integer versjon,
        Long forrigeKjoringId,
        String korrigeringsaarsak,
        Long sammenligningId,
        String topptekst,
        String bunntekst,
        String varReferanse) {

    /** Compatibility constructor for ordinary V1 fixtures and old call sites; texts are the defaults. */
    public Fakturakjoring(Long id, LocalDate periode, String status, Long prisversjonId,
                          Long bruksdataImportId, String generertAv, OffsetDateTime generertAt,
                          String godkjentAv, OffsetDateTime godkjentAt, String kommentar) {
        this(id, periode, status, prisversjonId, bruksdataImportId, generertAv, generertAt,
                godkjentAv, godkjentAt, kommentar, Kjoringstype.ORDINAER.name(), 1, null, null, null,
                Fakturatekster.standard(new Periode(periode)).topptekst(),
                Fakturatekster.STANDARD_BUNNTEKST, Fakturatekster.STANDARD_VAR_REFERANSE);
    }

    /** The billing period as Norwegian month and year, e.g. "januar 2027" (specs/005 FR-009). */
    public String periodeLabel() {
        return new Periode(periode).norskLabel();
    }

    public boolean erKorrigering() {
        return Kjoringstype.KORRIGERING.name().equals(type);
    }

    public String versjonLabel() {
        return "V" + versjon;
    }

    /**
     * The invoice texts the export writes (specs/009); null for a run exported or discarded before
     * texts were stored per run.
     */
    public Fakturatekster tekster() {
        if (topptekst == null && bunntekst == null && varReferanse == null) {
            return null;
        }
        return new Fakturatekster(topptekst, bunntekst, varReferanse);
    }

    public Fakturakjoring medStatus(String nyStatus, String nyGodkjentAv, OffsetDateTime nyGodkjentAt) {
        return new Fakturakjoring(id, periode, nyStatus, prisversjonId, bruksdataImportId,
                generertAv, generertAt, nyGodkjentAv, nyGodkjentAt, kommentar,
                type, versjon, forrigeKjoringId, korrigeringsaarsak, sammenligningId,
                topptekst, bunntekst, varReferanse);
    }

    public Fakturakjoring medTekster(Fakturatekster t) {
        return new Fakturakjoring(id, periode, status, prisversjonId, bruksdataImportId,
                generertAv, generertAt, godkjentAv, godkjentAt, kommentar,
                type, versjon, forrigeKjoringId, korrigeringsaarsak, sammenligningId,
                t.topptekst(), t.bunntekst(), t.varReferanse());
    }
}
