package no.digdir.forsystem.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The filters on the event log page (specs/014): a date range in Norwegian time (both days included), part of
 * a username, an entity type and an action. Empty values mean «no filter». A reading filter, not a billing
 * period.
 */
public record Hendelsesfilter(LocalDate fra, LocalDate til, String bruker, String entitet, String handling) {

    public static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    public Hendelsesfilter {
        bruker = tomTilNull(bruker);
        entitet = tomTilNull(entitet);
        handling = tomTilNull(handling);
    }

    public static Hendelsesfilter ingen() {
        return new Hendelsesfilter(null, null, null, null, null);
    }

    public boolean erTom() {
        return fra == null && til == null && bruker == null && entitet == null && handling == null;
    }

    /** A range that ends before it starts matches nothing; the page says so. */
    public boolean gyldig() {
        return fra == null || til == null || !fra.isAfter(til);
    }

    /** Start of the from-day, Oslo time. */
    public OffsetDateTime fraTidspunkt() {
        return fra == null ? null : fra.atStartOfDay(OSLO).toOffsetDateTime();
    }

    /** Start of the day after the to-day, Oslo time (exclusive upper bound). */
    public OffsetDateTime tilTidspunkt() {
        return til == null ? null : til.plusDays(1).atStartOfDay(OSLO).toOffsetDateTime();
    }

    /** The filters as a query string for paging and download links, e.g. {@code ?handling=GENERERTE&side=2}. */
    public String query(Integer side) {
        Map<String, String> p = verdier();
        if (side != null && side > 1) {
            p.put("side", side.toString());
        }
        if (p.isEmpty()) {
            return "";
        }
        List<String> deler = new ArrayList<>();
        p.forEach((k, v) -> deler.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return "?" + String.join("&", deler);
    }

    public String query() {
        return query(null);
    }

    /** The non-empty filters by parameter name, for links and the download's audit row. */
    public Map<String, String> verdier() {
        Map<String, String> p = new LinkedHashMap<>();
        if (fra != null) {
            p.put("fra", fra.toString());
        }
        if (til != null) {
            p.put("til", til.toString());
        }
        if (bruker != null) {
            p.put("bruker", bruker);
        }
        if (entitet != null) {
            p.put("entitet", entitet);
        }
        if (handling != null) {
            p.put("handling", handling);
        }
        return p;
    }

    private static String tomTilNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
