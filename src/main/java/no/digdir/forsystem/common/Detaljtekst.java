package no.digdir.forsystem.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The jsonb {@code detaljer} of an audit row as text a person can read and Excel can filter (specs/014):
 * {@code {"periode":"2027-01","endringer":{"topptekst":{"fra":"a","til":"b"}}}} reads
 * {@code periode: 2027-01; endringer.topptekst.fra: a; endringer.topptekst.til: b}. Lists of values are
 * joined with commas; lists of objects show each object in braces. Nothing is interpreted or left out.
 */
public final class Detaljtekst {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Detaljtekst() {
    }

    /** Readable text, or empty for no details. Invalid JSON is returned as it is. */
    public static String les(Jsonb detaljer) {
        if (detaljer == null || detaljer.json() == null || detaljer.json().isBlank()) {
            return "";
        }
        JsonNode node = tre(detaljer);
        return node == null ? detaljer.json() : tekst(node);
    }

    /**
     * The billing period the row is about, {@code yyyy-MM}, when the details carry a top-level {@code periode}
     * (as {@code 2027-01} or {@code 2027-01-01}); otherwise empty. Copied as recorded, never derived.
     */
    public static String periode(Jsonb detaljer) {
        JsonNode node = tre(detaljer);
        if (node == null || !node.isObject() || !node.path("periode").isTextual()) {
            return "";
        }
        String p = node.get("periode").asText();
        return p.length() >= 7 && p.charAt(4) == '-' ? p.substring(0, 7) : p;
    }

    private static JsonNode tre(Jsonb detaljer) {
        if (detaljer == null || detaljer.json() == null || detaljer.json().isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(detaljer.json());
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String tekst(JsonNode node) {
        if (node.isObject()) {
            List<String> par = new ArrayList<>();
            flat("", node, par);
            return String.join("; ", par);
        }
        return verdi(node);
    }

    private static void flat(String prefiks, JsonNode node, List<String> ut) {
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> felt = it.next();
            String navn = prefiks.isEmpty() ? felt.getKey() : prefiks + "." + felt.getKey();
            if (felt.getValue().isObject() && !felt.getValue().isEmpty()) {
                flat(navn, felt.getValue(), ut);
            } else {
                ut.add(navn + ": " + verdi(felt.getValue()));
            }
        }
    }

    private static String verdi(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            List<String> deler = new ArrayList<>();
            node.forEach(e -> deler.add(e.isObject() ? "{" + tekst(e) + "}" : verdi(e)));
            return String.join(", ", deler);
        }
        if (node.isObject()) {
            return node.isEmpty() ? "" : "{" + tekst(node) + "}";
        }
        return node.asText();
    }
}
