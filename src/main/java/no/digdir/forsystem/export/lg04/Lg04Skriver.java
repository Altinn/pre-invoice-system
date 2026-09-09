package no.digdir.forsystem.export.lg04;

/**
 * Byte-exact LG04 line renderer, ported from the reference system (../agresso Linje1/2/3). Each line
 * is exactly {@link #LINJELENGDE} characters, space-filled, with fields at fixed byte offsets. The
 * format is contractual — golden-file tests lock it character-by-character, so any change here must
 * be a conscious golden-file update (docs/05).
 *
 * <p>Differences from the reference (docs/04 Phase 4): values are parameterized rather than derived
 * from the clock or hardcoded — kontering comes from the product, responsible from configuration,
 * and {@code batchId}/period from the run.
 */
public final class Lg04Skriver {

    public static final int LINJELENGDE = 4324;

    // Format constants (not accounting codes — part of the LG04 layout itself).
    private static final String KLIENT = padRight("AR", 25);
    private static final String BETALINGSBETINGELSE = padRight("30", 25);
    private static final String TRANSTYPE = "42";
    private static final String VERDI_1 = padLeft("100", 20);
    private static final String BILAGSTYPE = "SO";
    private static final String ORDRETYPE = "FS";
    private static final String STATUS = "N";
    private static final String BELOP_SATT = "1";
    private static final String HJELPETEKST = "Klikk på lenken for å se fakturagrunnlaget";
    private static final String LINJE_NULL = "0";
    private static final String LINJE_EN = "1";

    /** Header line (line "0"): references, recipient, order metadata. */
    public String renderLinje1(Lg04Ordre o) {
        char[] buf = tomLinje();
        plasser(buf, 25, forkort(o.fakturareferanse(), 25));
        plasser(buf, 233, padRight(nn(o.kundenummer()), 25));
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 2368, forkort(o.bestillingsnummer(), 25));
        plasser(buf, 2493, LINJE_NULL);
        plasser(buf, 2509, HJELPETEKST);
        plasser(buf, 2629, padRight(nn(o.url()), 120));
        plasser(buf, 3130, padRight(String.valueOf(o.ordreNr()), 15));
        plasser(buf, 3145, ORDRETYPE);
        plasser(buf, 3264, nn(o.ansvarlig1()));
        plasser(buf, 3289, nn(o.ansvarlig2()));
        plasser(buf, 3410, STATUS);
        plasser(buf, 3519, BETALINGSBETINGELSE);
        plasser(buf, 3944, TRANSTYPE);
        plasser(buf, 4259, BILAGSTYPE);
        return new String(buf);
    }

    /** Amount line (line "1"): account, dimensions, article, amount in øre, description. */
    public String renderLinje2(Lg04Ordre o) {
        char[] buf = tomLinje();
        plasser(buf, 0, padRight(nn(o.konto()), 25));
        plasser(buf, 212, nn(o.belopOre()));
        plasser(buf, 232, BELOP_SATT);
        plasser(buf, 538, nn(o.beskrivelse()));
        plasser(buf, 793, padRight(nn(o.artikkel()), 25));
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 965, padRight(nn(o.belopOre()), 20));
        plasser(buf, 1908, padRight(nn(o.dim1()), 25));
        plasser(buf, 1933, padRight(nn(o.dim2()), 25));
        plasser(buf, 1983, padRight(nn(o.dim4()), 25));
        plasser(buf, 2493, LINJE_EN);
        plasser(buf, 2509, HJELPETEKST);
        plasser(buf, 2629, padRight(nn(o.url()), 120));
        plasser(buf, 3130, padRight(String.valueOf(o.ordreNr()), 15));
        plasser(buf, 3145, ORDRETYPE);
        plasser(buf, 3410, STATUS);
        plasser(buf, 3519, BETALINGSBETINGELSE);
        plasser(buf, 3944, TRANSTYPE);
        plasser(buf, 4224, VERDI_1);
        plasser(buf, 4259, BILAGSTYPE);
        return new String(buf);
    }

    /** Trailer line (line "1" with sequence number). */
    public String renderLinje3(Lg04Ordre o) {
        char[] buf = tomLinje();
        plasser(buf, 881, o.batchId());
        plasser(buf, 906, KLIENT);
        plasser(buf, 2493, LINJE_EN);
        plasser(buf, 2509, HJELPETEKST);
        plasser(buf, 2629, padRight(nn(o.url()), 120));
        plasser(buf, 3130, padRight(String.valueOf(o.ordreNr()), 15));
        plasser(buf, 3145, ORDRETYPE);
        plasser(buf, 3314, String.valueOf(o.sekvensnr()));
        plasser(buf, 3410, STATUS);
        plasser(buf, 3519, BETALINGSBETINGELSE);
        plasser(buf, 3944, TRANSTYPE);
        plasser(buf, 4259, BILAGSTYPE);
        return new String(buf);
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
