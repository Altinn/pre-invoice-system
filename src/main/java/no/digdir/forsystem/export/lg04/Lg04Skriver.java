package no.digdir.forsystem.export.lg04;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Byte-exact LG04 line renderer. Each line is exactly {@link #LINJELENGDE} characters, space-filled, with
 * fields at fixed byte offsets. An order is a header line (line number 0) followed by one line pair per
 * product, numbered 1, 2, 3… within the order — the Altinn format FEL's LG04 program writes for FinMod
 * (OQ-1, OQ-23). The format is contractual — the golden-file test locks it byte-by-byte against FEL's
 * sample, so any change here must be a conscious golden-file update (docs/05).
 *
 * <p>Ported from the reference system (../agresso Linje1/2/3), whose one-product-per-order layout is the
 * DPI/eSignering format. Values are parameterized rather than derived from the clock or hardcoded —
 * kontering comes from the product, responsible from configuration, and {@code batchId}/text from the
 * caller.
 */
public final class Lg04Skriver {

    public static final int LINJELENGDE = 4324;

    // Format constants (not accounting codes — part of the LG04 layout itself).
    private static final String KLIENT = padRight("AR", 25);
    private static final String BETALINGSBETINGELSE = padRight("30", 25);
    private static final String TRANSTYPE = "42";
    private static final String BILAGSTYPE = "SO";
    private static final String ORDRETYPE = "FS";
    private static final String STATUS = "N";
    private static final String BELOP_SATT = "1";
    private static final String SEKVENSNR = "8";
    // TODO(OQ-23): FEL writes -100 when there is no quantity (cost lines); its meaning in Unit4 is unconfirmed.
    private static final String VERDI_1_UTEN_ANTALL = "-100";
    private static final String LINJE_NULL = "0";

    /** Header line (line number 0): references, recipient, order metadata. */
    public String renderLinje1(Lg04Ordre o) {
        char[] buf = tomLinje();
        plasser(buf, 25, forkort(o.fakturareferanse(), 25));
        plasser(buf, 233, padRight(nn(o.kundenummer()), 25));
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 2368, forkort(o.bestillingsnummer(), 25));
        plasser(buf, 2493, LINJE_NULL);
        felles(buf, o);
        plasser(buf, 3264, nn(o.ansvarlig1()));
        plasser(buf, 3289, nn(o.ansvarlig2()));
        return new String(buf);
    }

    /** Amount line of product {@code linjenr} (1-based): account, dimensions, article, amount in øre, quantity. */
    public String renderLinje2(Lg04Ordre o, int linjenr) {
        Lg04Ordre.Lg04Ordrelinje l = o.linjer().get(linjenr - 1);
        char[] buf = tomLinje();
        plasser(buf, 0, padRight(nn(l.konto()), 25));
        plasser(buf, 212, padLeft(nn(l.belopOre()), 20));
        plasser(buf, 232, BELOP_SATT);
        plasser(buf, 538, nn(l.produktnavn()));
        plasser(buf, 793, padRight(nn(l.artikkel()), 25));
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 965, padLeft(nn(l.belopOre()), 20));
        plasser(buf, 1908, padRight(nn(l.dim1()), 25));
        plasser(buf, 1933, padRight(nn(l.dim2()), 25));
        plasser(buf, 1983, padRight(nn(l.dim4()), 25));
        plasser(buf, 2493, String.valueOf(linjenr));
        felles(buf, o);
        plasser(buf, 4224, padLeft(verdi1(l.antall()), 20));
        return new String(buf);
    }

    /** Second line of product {@code linjenr}'s pair, with the fixed sequence number. */
    public String renderLinje3(Lg04Ordre o, int linjenr) {
        char[] buf = tomLinje();
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 2493, String.valueOf(linjenr));
        felles(buf, o);
        plasser(buf, 3314, SEKVENSNR);
        return new String(buf);
    }

    /** Fields every line of the order carries. */
    private static void felles(char[] buf, Lg04Ordre o) {
        plasser(buf, 2509, padRight(nn(o.tekst()), 120));
        plasser(buf, 2629, padRight(nn(o.url()), 120));
        plasser(buf, 3130, padRight(String.valueOf(o.ordreNr()), 15));
        plasser(buf, 3145, ORDRETYPE);
        plasser(buf, 3410, STATUS);
        plasser(buf, 3519, BETALINGSBETINGELSE);
        plasser(buf, 3944, TRANSTYPE);
        plasser(buf, 4259, BILAGSTYPE);
    }

    /** VALUE_1: the quantity in hundredths, as FEL's program writes it. */
    private static String verdi1(BigDecimal antall) {
        if (antall == null || antall.signum() == 0) {
            return VERDI_1_UTEN_ANTALL;
        }
        return antall.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static char[] tomLinje() {
        char[] buf = new char[LINJELENGDE];
        java.util.Arrays.fill(buf, ' ');
        return buf;
    }

    private static void plasser(char[] buf, int delta, String verdi) {
        for (int i = 0; i < verdi.length() && delta + i < buf.length; i++) {
            buf[delta + i] = verdi.charAt(i);
        }
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private static String padLeft(String s, int n) {
        return String.format("%" + n + "s", s);
    }

    /** Mirrors commons-lang {@code StringUtils.abbreviate}: keep short values, ellipsize longer ones. */
    private static String forkort(String s, int maks) {
        String v = nn(s);
        if (v.length() <= maks) {
            return v;
        }
        return v.substring(0, maks - 3) + "...";
    }
}
