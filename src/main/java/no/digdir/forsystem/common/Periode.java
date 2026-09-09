package no.digdir.forsystem.common;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A billing period: always the first day of a month (docs/02). The period is an explicit parameter
 * through every layer and is never derived from the clock — deriving "now" was a defect class in
 * the old system. Stored in the database as a {@code date} via {@link #førsteDag()}.
 */
public record Periode(LocalDate førsteDag) {

    private static final String[] MÅNEDER = {
            "januar", "februar", "mars", "april", "mai", "juni",
            "juli", "august", "september", "oktober", "november", "desember"};

    public Periode {
        if (førsteDag.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("Periode må være den første dagen i en måned: " + førsteDag);
        }
    }

    public static Periode av(int år, int måned) {
        return new Periode(LocalDate.of(år, måned, 1));
    }

    public static Periode fra(YearMonth årMåned) {
        return new Periode(årMåned.atDay(1));
    }

    /** Parse an ISO year-month such as {@code "2027-01"} (the value of an HTML month input). */
    public static Periode parse(String årMåned) {
        return fra(YearMonth.parse(årMåned));
    }

    public Periode forrige() {
        return new Periode(førsteDag.minusMonths(1));
    }

    public YearMonth årMåned() {
        return YearMonth.from(førsteDag);
    }

    /** ISO form {@code "2027-01"} — stable, used in keys and machine contexts. */
    @Override
    public String toString() {
        return årMåned().toString();
    }

    /** Human label {@code "januar 2027"} — used in invoice line descriptions (docs/03). */
    public String norskLabel() {
        return MÅNEDER[førsteDag.getMonthValue() - 1] + " " + førsteDag.getYear();
    }
}
