package no.digdir.forsystem.usage;

import java.time.YearMonth;

import no.digdir.forsystem.common.Periode;

/**
 * What a row of a flagged datavarehus export is to the import of one period (specs/017). The flags
 * decide, never {@code regel}: that text is shown as evidence only (FR-013).
 */
enum DwhRadklasse {
    /** Flagged for Altinn invoicing and dated in the period: becomes usage. */
    BRUKT,
    /** Dated in another month than the declared period: reported, never an error. */
    UTENFOR_PERIODE,
    /** Flagged DPV: not billed here (OQ-26), counted and reported. */
    DPV,
    /** Neither flag: left out by the datavarehus (zero volume, inactive agreement …), reported. */
    EKSKLUDERT;

    /** Month check first, so a flagged row in another month is «other month», never «used». */
    static DwhRadklasse klassifiser(DwhRad rad, Periode periode) {
        if (!periode.årMåned().equals(YearMonth.from(rad.dato()))) {
            return UTENFOR_PERIODE;
        }
        if (Boolean.TRUE.equals(rad.flaggAltinn())) {
            return BRUKT;
        }
        if (Boolean.TRUE.equals(rad.flaggDpv())) {
            return DPV;
        }
        return EKSKLUDERT;
    }
}
