package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import no.digdir.forsystem.common.Norsktall;
import org.springframework.stereotype.Component;

/**
 * The one number format on every page (specs/010 FR-001/FR-002): a space between thousands and a comma
 * before decimals, as Økonomi reads figures — 1 234 567,89. Templates call it as {@code ${@tall.belop(x)}}.
 * Display only: stored values and the LG04 file are untouched.
 */
@Component("tall")
public class Tallformat {

    private static final String MANGLER = "—";

    /** An amount in kroner: two decimals, rounded half up. */
    public String belop(BigDecimal v) {
        return v == null ? MANGLER : Norsktall.toDesimaler(v);
    }

    /** A count of rows, lines or invoices: whole, no decimals. */
    public String antall(Number v) {
        return v == null ? MANGLER : format("#,##0").format(v);
    }

    /** A usage quantity: two decimals, like amounts (source data may carry decimals). */
    public String mengde(BigDecimal v) {
        return belop(v);
    }

    /** A unit price: every stored decimal, at least two, so a price of 0,0375 kr is never shown rounded. */
    public String pris(BigDecimal v) {
        if (v == null) {
            return MANGLER;
        }
        int desimaler = Math.max(2, v.stripTrailingZeros().scale());
        return format("#,##0." + "0".repeat(desimaler)).format(v.setScale(desimaler, RoundingMode.HALF_UP));
    }

    private static DecimalFormat format(String moenster) {
        return Norsktall.format(moenster);
    }
}
