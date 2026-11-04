package no.digdir.forsystem.billing;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;

/**
 * The three texts every LG04 order of a run carries (specs/009, docs/07 OQ-23): the header
 * ({@code long_info1}), the footer ({@code long_info2}) and «Vår referanse» ({@code responsible} and
 * {@code responsible2}). Set to Økonomi's defaults at generation, editable while the run is GENERERT,
 * frozen from approval on.
 *
 * <p>The hard limits are the LG04 field widths (FEL, 2026-11-04). Regnskap's invoice template suggests
 * shorter texts on the printed invoice; FEL has not confirmed which limit applies there, so those are
 * only warnings — TODO(OQ-23).
 */
public record Fakturatekster(String topptekst, String bunntekst, String varReferanse) {

    /** LG04 {@code long_info1}/{@code long_info2} are c120 (LG04_55). */
    public static final int MAKS_TEKST = 120;
    /** LG04 {@code responsible}/{@code responsible2} are c25. */
    public static final int MAKS_VAR_REFERANSE = 25;
    /** Regnskap's template, not confirmed by FEL for the printed invoice — TODO(OQ-23). */
    public static final int TRYKK_TOPPTEKST = 90;
    /** Regnskap's template, not confirmed by FEL for the printed invoice — TODO(OQ-23). */
    public static final int TRYKK_BUNNTEKST = 65;

    // Økonomi's defaults (project kickoff page, answered 2026-11-04).
    static final String STANDARD_TOPPTEKST_START = "Bruk av Altinn ";
    static final String STANDARD_BUNNTEKST = "Se samarbeid.digdir.no for priser og innsyn i egen statistikk";
    static final String STANDARD_VAR_REFERANSE = "Servicedesk@altinn.no";

    private static final Charset WIN_1252 = Charset.forName("windows-1252");

    /** The defaults for a run of {@code periode}: header «Bruk av Altinn januar 2027». */
    public static Fakturatekster standard(Periode periode) {
        return new Fakturatekster(STANDARD_TOPPTEKST_START + periode.norskLabel(), STANDARD_BUNNTEKST,
                STANDARD_VAR_REFERANSE);
    }

    /** The same texts with leading and trailing whitespace removed; null stays null. */
    public Fakturatekster normalisert() {
        return new Fakturatekster(trim(topptekst), trim(bunntekst), trim(varReferanse));
    }

    /**
     * Refuse texts the LG04 file cannot carry as written: empty, longer than the field, with a line
     * break or other control character (it would break the fixed-width line), or with a character
     * Windows-1252 cannot represent (it would be written as {@code ?}). Call on {@link #normalisert()}.
     */
    public void valider() {
        sjekk("Topptekst", topptekst, MAKS_TEKST);
        sjekk("Bunntekst", bunntekst, MAKS_TEKST);
        sjekk("Vår referanse", varReferanse, MAKS_VAR_REFERANSE);
    }

    /** Field name → {fra, til} for each text that differs from {@code forrige}, in form order. */
    public Map<String, Map<String, String>> endringerFra(Fakturatekster forrige) {
        Map<String, Map<String, String>> endringer = new LinkedHashMap<>();
        endring(endringer, "topptekst", forrige == null ? null : forrige.topptekst(), topptekst);
        endring(endringer, "bunntekst", forrige == null ? null : forrige.bunntekst(), bunntekst);
        endring(endringer, "varReferanse", forrige == null ? null : forrige.varReferanse(), varReferanse);
        return endringer;
    }

    public boolean topptekstOverTrykkgrense() {
        return topptekst != null && topptekst.length() > TRYKK_TOPPTEKST;
    }

    public boolean bunntekstOverTrykkgrense() {
        return bunntekst != null && bunntekst.length() > TRYKK_BUNNTEKST;
    }

    private static void sjekk(String felt, String verdi, int maks) {
        if (verdi == null || verdi.isBlank()) {
            throw new Regelbrudd(felt + " kan ikke være tom");
        }
        if (verdi.length() > maks) {
            throw new Regelbrudd(felt + " er " + verdi.length() + " tegn, men LG04-feltet har plass til "
                    + maks);
        }
        CharsetEncoder encoder = WIN_1252.newEncoder();
        for (int i = 0; i < verdi.length(); ) {
            int tegn = verdi.codePointAt(i);
            if (Character.isISOControl(tegn)) {
                throw new Regelbrudd(felt + " kan ikke inneholde linjeskift, tabulator eller andre kontrolltegn");
            }
            String s = new String(Character.toChars(tegn));
            if (!encoder.canEncode(s)) {
                throw new Regelbrudd(felt + " inneholder «" + s + "», som LG04-filen (Windows-1252) ikke kan lagre");
            }
            i += Character.charCount(tegn);
        }
    }

    private static void endring(Map<String, Map<String, String>> endringer, String felt, String fra, String til) {
        if (fra == null ? til != null : !fra.equals(til)) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("fra", fra);
            e.put("til", til);
            endringer.put(felt, e);
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }
}
