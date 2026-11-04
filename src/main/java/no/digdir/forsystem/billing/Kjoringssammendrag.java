package no.digdir.forsystem.billing;

import java.math.BigDecimal;

import no.digdir.forsystem.common.Periode;

/**
 * The run's headline figures for the control before approval and the covering email to regnskap
 * (specs/002 FR-001..003, BF1 T15). Computed from the stored invoices and lines, never stored.
 * {@code sumBelop} is the sum of the lines; {@code sumFakturaer} the sum of the invoices' totals — they
 * are equal by construction, and {@link #konsistent()} makes a defect visible if they are not.
 * Amounts are ex. mva. {@code sumInklMva} is each invoice's sum × {@link #MVA_FAKTOR} rounded to øre,
 * then summed — FEL's method (2026-11-04, OQ-11), closer to Unit4's import report than total × 1.25.
 */
public record Kjoringssammendrag(
        Periode periode,
        int antallFakturaer,
        int antallLinjer,
        BigDecimal sumBelop,
        BigDecimal sumFakturaer,
        BigDecimal sumInklMva) {

    /** 25 % mva on every Altinn product (FEL, 2026-11-04). */
    public static final BigDecimal MVA_FAKTOR = new BigDecimal("1.25");

    public boolean konsistent() {
        return sumBelop.compareTo(sumFakturaer) == 0;
    }
}
