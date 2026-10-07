package no.digdir.forsystem.usage;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public static List<DwhRad> les(byte[] raadata) {
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
        List<DwhRad> rader = new ArrayList<>();
        int i = 0;
        for (JsonNode n : rot.get("value")) {
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
