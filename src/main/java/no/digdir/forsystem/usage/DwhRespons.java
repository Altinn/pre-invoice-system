package no.digdir.forsystem.usage;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;

/**
 * Parses a Data API Builder response for {@code mv_altinn_usage_monthly} into {@link DwhRad}s
 * (contract: specs/001-dwh-usage-import/contracts/dwh-api.md). Strict on what billing depends on,
 * tolerant of extra fields.
 *
 * <p>A {@code nextLink} means the response is only one page. DAB pages by the entity key, which for
 * this view is {@code organisation_number} alone although rows are per org × product × day, so
 * following it silently drops rows (OQ-19). Any {@code nextLink} therefore fails the whole fetch.
 */
public final class DwhRespons {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DwhRespons() {
    }

    /** The {@code value} list of a response; refuses anything that is not one complete page. */
    private static JsonNode verdier(byte[] raadata) {
        JsonNode rot;
        try {
            rot = JSON.readTree(raadata);
        } catch (IOException e) {
            throw new Regelbrudd("Ugyldig svar fra datavarehus: ikke gyldig JSON");
        }
        if (rot == null || !rot.isObject() || !rot.path("value").isArray()) {
            throw new Regelbrudd("Ugyldig svar fra datavarehus: mangler «value»-liste");
        }
        JsonNode neste = rot.get("nextLink");
        if (neste != null && !neste.isNull() && !neste.asText().isBlank()) {
            throw new Regelbrudd("Ufullstendig svar fra datavarehus: svaret har flere sider. "
                    + "Øk forsystem.dwh.maks-rader eller få DWH-teamet til å rette nøkkelen på viewet (OQ-19).");
        }
        return rot.get("value");
    }

    /**
     * The response as a flagged export (specs/017) if its rows carry {@code flag_inv_altinn}, else null
     * (an older response without flags, read by {@link #les}). The rows go through the same classification
     * as an uploaded file: flag-1 rows in the period are used, the rest is counted and never an error.
     */
    public static DwhCsv.Fil lesFlagget(byte[] raadata, Periode periode) {
        JsonNode verdier = verdier(raadata);
        if (verdier.isEmpty() || !verdier.get(0).has(DwhCsv.FLAGG_ALTINN)) {
            return null;
        }
        List<Map<String, String>> rader = new ArrayList<>();
        for (JsonNode n : verdier) {
            Map<String, String> r = new LinkedHashMap<>();
            n.fields().forEachRemaining(f -> r.put(f.getKey().toLowerCase(Locale.ROOT), feltverdi(f.getValue())));
            rader.add(r);
        }
        return DwhCsv.lesRader(rader, periode);
    }

    /** Text for a JSON value, null for null/empty; numbers in plain notation so no precision is lost. */
    private static String feltverdi(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.isNumber() ? v.decimalValue().toPlainString() : v.asText().strip();
        return s.isEmpty() ? null : s;
    }

    public static List<DwhRad> les(byte[] raadata) {
        JsonNode verdier = verdier(raadata);
        List<DwhRad> rader = new ArrayList<>();
        int i = 0;
        for (JsonNode n : verdier) {
            i++;
            rader.add(new DwhRad(
                    påkrevdTekst(n, "organisation_number", i),
                    påkrevdTekst(n, "product_name", i),
                    tall(n, "quantity", i),
                    tall(n, "amount_nok", i),
                    dato(påkrevdTekst(n, "transaction_date", i), i),
                    tekst(n, "kundenummer"),
                    tekst(n, "fakturareferanse")));
        }
        return rader;
    }

    private static String påkrevdTekst(JsonNode n, String felt, int i) {
        String v = tekst(n, felt);
        if (v == null) {
            throw new Regelbrudd("Ugyldig svar fra datavarehus: rad " + i + " mangler " + felt);
        }
        return v;
    }

    private static String tekst(JsonNode n, String felt) {
        JsonNode v = n.get(felt);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal tall(JsonNode n, String felt, int i) {
        JsonNode v = n.get(felt);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new Regelbrudd("Ugyldig svar fra datavarehus: rad " + i + " har ikke-numerisk " + felt);
        }
        return v.decimalValue();
    }

    private static LocalDate dato(String tekst, int i) {
        try {
            // DAB renders the date column as "YYYY-MM-DD"; tolerate a time suffix.
            return LocalDate.parse(tekst.length() > 10 ? tekst.substring(0, 10) : tekst);
        } catch (DateTimeParseException e) {
            throw new Regelbrudd("Ugyldig svar fra datavarehus: rad " + i + " har ugyldig transaction_date " + tekst);
        }
    }
}
