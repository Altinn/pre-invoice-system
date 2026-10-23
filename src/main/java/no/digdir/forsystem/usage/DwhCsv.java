package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import no.digdir.forsystem.common.Regelbrudd;

/**
 * Reads a CSV export of the datavarehus view {@code mv_altinn_usage_monthly} — the fallback while the
 * API cannot be reached (OQ-19). The rows carry the same fields as the API's JSON, so they go through
 * the same aggregation, product-name mapping and registry cross-check (specs/001).
 *
 * <p>Format as the datavarehus exports it: comma-separated, double-quoted text, a header row, the
 * literal {@code NULL} for missing values. The date column has had three names: {@code maaned} and
 * {@code transaction_month} (monthly exports, the view has been renamed once already) and
 * {@code transaction_date} (daily export, as in the API); any one is accepted. Other columns
 * ({@code antall_rader}, {@code days_with_data}, {@code source_table}) are ignored.
 */
public final class DwhCsv {

    /** Columns every export must have (besides one of {@link #DATOKOLONNER}). */
    static final List<String> KOLONNER = List.of("organisation_number", "product_name", "quantity", "amount_nok");

    /** Accepted names of the date column, in order of preference. */
    static final List<String> DATOKOLONNER = List.of("transaction_date", "transaction_month", "maaned");

    /** A customer as the export describes it, for creating missing customers. */
    public record KundeIFil(String organisasjonsnummer, String navn, String kundenummer, String fakturareferanse,
                            String bestillingsnummer) {
    }

    private DwhCsv() {
    }

    public static List<DwhRad> les(byte[] innhold) {
        List<DwhRad> rader = new ArrayList<>();
        Tabell t = tabell(innhold);
        for (int i = 0; i < t.rader.size(); i++) {
            Map<String, String> r = t.rader.get(i);
            int linje = i + 2;
            rader.add(new DwhRad(
                    påkrevd(r, "organisation_number", linje),
                    påkrevd(r, "product_name", linje),
                    tall(r.get("quantity"), "quantity", linje),
                    tall(r.get("amount_nok"), "amount_nok", linje),
                    dato(r, linje),
                    r.get("kundenummer"),
                    r.get("fakturareferanse")));
        }
        return rader;
    }

    /** One entry per organisation, in file order: name, kundenummer and references from its first row. */
    public static List<KundeIFil> kunder(byte[] innhold) {
        Map<String, KundeIFil> perOrgnr = new LinkedHashMap<>();
        Tabell t = tabell(innhold);
        for (int i = 0; i < t.rader.size(); i++) {
            Map<String, String> r = t.rader.get(i);
            String orgnr = påkrevd(r, "organisation_number", i + 2);
            perOrgnr.putIfAbsent(orgnr, new KundeIFil(orgnr, r.get("organisation_name"), r.get("kundenummer"),
                    r.get("fakturareferanse"), r.get("bestillingsnummer")));
        }
        return List.copyOf(perOrgnr.values());
    }

    private record Tabell(List<Map<String, String>> rader) {
    }

    private static Tabell tabell(byte[] innhold) {
        String tekst = new String(innhold, StandardCharsets.UTF_8);
        if (tekst.startsWith("﻿")) {
            tekst = tekst.substring(1);
        }
        List<List<String>> linjer = parse(tekst);
        if (linjer.isEmpty()) {
            throw new Regelbrudd("Fila er tom");
        }
        List<String> hode = linjer.get(0).stream()
                .map(h -> h.replace(SITERT, "").strip().toLowerCase(Locale.ROOT)).toList();
        List<String> mangler = KOLONNER.stream().filter(k -> !hode.contains(k)).toList();
        if (!mangler.isEmpty()) {
            throw new Regelbrudd("Fila ser ikke ut som en eksport fra datavarehuset: mangler "
                    + String.join(", ", mangler) + ". " + Bruksdatafil.funnet(hode));
        }
        if (DATOKOLONNER.stream().noneMatch(hode::contains)) {
            throw new Regelbrudd("Eksporten fra datavarehuset mangler datokolonne (en av "
                    + String.join(", ", DATOKOLONNER) + "). " + Bruksdatafil.funnet(hode));
        }
        List<Map<String, String>> rader = new ArrayList<>();
        for (int i = 1; i < linjer.size(); i++) {
            List<String> felt = linjer.get(i);
            if (felt.size() == 1 && felt.get(0).isBlank()) {
                continue;
            }
            if (felt.size() != hode.size()) {
                throw new Regelbrudd("Linje " + (i + 1) + " har " + felt.size() + " felt, overskriften har " + hode.size());
            }
            Map<String, String> rad = new HashMap<>();
            for (int k = 0; k < hode.size(); k++) {
                rad.put(hode.get(k), verdi(felt.get(k)));
            }
            rader.add(rad);
        }
        return new Tabell(rader);
    }

    /** RFC 4180: quoted fields may contain commas, doubled quotes and newlines. */
    private static List<List<String>> parse(String tekst) {
        List<List<String>> linjer = new ArrayList<>();
        List<String> felt = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean iSitat = false;
        boolean varSitert = false;
        for (int i = 0; i < tekst.length(); i++) {
            char c = tekst.charAt(i);
            if (iSitat) {
                if (c == '"' && i + 1 < tekst.length() && tekst.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else if (c == '"') {
                    iSitat = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                iSitat = true;
                varSitert = true;
            } else if (c == ',') {
                felt.add(markert(sb, varSitert));
                sb.setLength(0);
                varSitert = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < tekst.length() && tekst.charAt(i + 1) == '\n') {
                    i++;
                }
                felt.add(markert(sb, varSitert));
                linjer.add(felt);
                felt = new ArrayList<>();
                sb.setLength(0);
                varSitert = false;
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0 || !felt.isEmpty()) {
            felt.add(markert(sb, varSitert));
            linjer.add(felt);
        }
        return linjer;
    }

    // A quoted "NULL" is text; only the bare NULL token means missing. Mark quoted values to tell them apart.
    private static final String SITERT = "\u0000";

    private static String markert(StringBuilder sb, boolean sitert) {
        return sitert ? SITERT + sb : sb.toString();
    }

    private static String verdi(String felt) {
        if (felt.startsWith(SITERT)) {
            String v = felt.substring(1);
            return v.isBlank() ? null : v;
        }
        String v = felt.strip();
        return v.isEmpty() || v.equals("NULL") ? null : v;
    }

    private static String påkrevd(Map<String, String> r, String felt, int linje) {
        String v = r.get(felt);
        if (v == null || v.isBlank()) {
            throw new Regelbrudd("Linje " + linje + " mangler " + felt);
        }
        return v.strip();
    }

    private static BigDecimal tall(String v, String felt, int linje) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.strip());
        } catch (NumberFormatException e) {
            throw new Regelbrudd("Linje " + linje + ": " + felt + " er ikke et tall: «" + v + "»");
        }
    }

    private static LocalDate dato(Map<String, String> r, int linje) {
        String v = DATOKOLONNER.stream().map(r::get).filter(d -> d != null).findFirst().orElse(null);
        if (v == null) {
            throw new Regelbrudd("Linje " + linje + " mangler dato");
        }
        try {
            return LocalDate.parse(v.strip().substring(0, Math.min(10, v.strip().length())));
        } catch (DateTimeParseException e) {
            throw new Regelbrudd("Linje " + linje + ": ugyldig dato «" + v + "» (forventet ÅÅÅÅ-MM-DD)");
        }
    }
}
