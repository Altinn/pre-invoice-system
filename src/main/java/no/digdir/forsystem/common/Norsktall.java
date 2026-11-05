package no.digdir.forsystem.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * The one Norwegian number format (specs/010 FR-001): a space between thousands and a comma before
 * decimals — 1 234 567,89. Pages use it through {@code web.Tallformat}; server-built texts (e.g. import
 * warnings) call it directly. Display only: stored values and the LG04 file are untouched.
 */
public final class Norsktall {

    private Norsktall() {
    }

    /** Two decimals, rounded half up: 1 234 567,89. */
    public static String toDesimaler(BigDecimal v) {
        return format("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP));
    }

    // DecimalFormat is not thread-safe, so each call gets its own.
    public static DecimalFormat format(String moenster) {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        s.setMinusSign('-');
        DecimalFormat f = new DecimalFormat(moenster, s);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f;
    }
}
