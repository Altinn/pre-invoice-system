package no.digdir.forsystem.billing;

import java.math.BigDecimal;

import no.digdir.forsystem.common.Periode;

/**
 * The run's headline figures for the control before approval and the covering email to regnskap
 * (specs/002 FR-001..003, BF1 T15). Computed from the stored invoices and lines, never stored.
 * {@code sumBelop} is the sum of the lines; {@code sumFakturaer} the sum of the invoices' totals — they
 * are equal by construction, and {@link #konsistent()} makes a defect visible if they are not.
 * Whether amounts include VAT is open (OQ-11), so no VAT figure exists here.
 */
public record Kjoringssammendrag(
        Periode periode,
        int antallFakturaer,
        int antallLinjer,
        BigDecimal sumBelop,
        BigDecimal sumFakturaer) {

    public boolean konsistent() {
        return sumBelop.compareTo(sumFakturaer) == 0;
    }
}
