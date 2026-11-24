package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import no.digdir.forsystem.common.Periode;
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
 * ({@code antall_rader}, {@code days_with_data}) are ignored.
 *
 * <p>The flagged export (specs/017) adds {@code flag_inv_altinn}, {@code flag_inv_dpv}, {@code source_table},
 * {@code resource_id} and {@code regel}. When {@code flag_inv_altinn} is in the header the file is
 * classified row by row ({@link DwhRadklasse}) and only the DWH's own «bill on the Altinn invoice» rows
 * in the declared period are validated and used.
 */
public final class DwhCsv {

    /** Columns every export must have (besides one of {@link #DATOKOLONNER}). */
    static final List<String> KOLONNER = List.of("organisation_number", "product_name", "quantity", "amount_nok");

    /** Accepted names of the date column, in order of preference. */
    static final List<String> DATOKOLONNER = List.of("transaction_date", "transaction_month", "maaned");

    /** Column that marks the flagged export. */
    static final String FLAGG_ALTINN = "flag_inv_altinn";
    static final String FLAGG_DPV = "flag_inv_dpv";

    private static final String URN_PREFIKS = "urn:altinn:resource:";
    private static final String UKJENT_ORG = "ukjent orgnr";

    /**
     * What a file yields for one period. {@code brukte} are the rows that go on to aggregation: every row of
     * an older export, or the flag-1 rows dated in the period of a flagged export. {@code avviste} are rows
     * the reader rejected (flagged files only; an older file's problems still abort the read).
     * {@code rapport} is null for an older export.
     */
    public record Fil(boolean flagget, List<DwhRad> brukte, List<AvvistRad> avviste, DwhFilrapport rapport,
                      List<String> advarsler) {

        /** The customer values of the used rows (specs/018). */
        public List<Kundedata> kunder() {
            return DwhRad.kundedata(brukte);
        }
    }

    private DwhCsv() {
    }

    /** All rows of an older (unflagged) export, for callers that have no period. */
    public static List<DwhRad> les(byte[] innhold) {
        return lesFil(innhold, null).brukte();
    }


    /**
     * Reads the export in one pass. An older export is read strictly, as before. A flagged export is
     * classified per row first: only flag-1 rows in {@code periode} are validated and kept; everything
     * else is counted for the report and never causes an error, however malformed its orgnr or quantity.
     */
    public static Fil lesFil(byte[] innhold, Periode periode) {
        Leser leser = new Leser(periode);
        String tekst = new String(innhold, StandardCharsets.UTF_8);
        if (tekst.startsWith("﻿")) {
            tekst = tekst.substring(1);
        }
        int[] linjenr = {0};
        parse(tekst, felt -> {
            linjenr[0]++;
            leser.linje(felt, linjenr[0]);
        });
        return leser.ferdig();
    }

    /**
     * Reads rows that are already split into columns — the objects of an API response — the same way as a
     * file: same header check, same flag classification, same report. The columns are those of the first row.
     */
    static Fil lesRader(List<Map<String, String>> rader, Periode periode) {
        Leser leser = new Leser(periode);
        int i = 0;
        for (Map<String, String> r : rader) {
            i++;
            if (leser.hode == null) {
                leser.lesHode(new ArrayList<>(r.keySet()));
            }
            leser.rad(r, i);
        }
        return leser.ferdig();
    }

    private static final class Leser {
        private final Periode periode;
        private List<String> hode;
        private boolean flagget;
        private final List<DwhRad> brukte = new ArrayList<>();
        private final List<AvvistRad> avviste = new ArrayList<>();
        private int totalt;
        private int antallBrukt;
        private int utenfor;
        private int dpv;
        private int utelatt;
        private final Map<YearMonth, Integer> utenforPerMaaned = new TreeMap<>();
        private final Map<DwhRadklasse, Map<String, BigDecimal>> volum = new EnumMap<>(DwhRadklasse.class);
        private final Map<String, Integer> arsaker = new LinkedHashMap<>();
        private final Map<String, BigDecimal> utelattPerOrg = new LinkedHashMap<>();

        Leser(Periode periode) {
            this.periode = periode;
        }

        void linje(List<String> felt, int linje) {
            boolean blank = felt.size() == 1 && felt.get(0).isBlank();
            if (blank) {
                return;
            }
            if (hode == null) {
                lesHode(felt);
                return;
            }
            if (felt.size() != hode.size()) {
                throw new Regelbrudd("Linje " + linje + " har " + felt.size() + " felt, overskriften har " + hode.size());
            }
            Map<String, String> r = new HashMap<>();
            for (int k = 0; k < hode.size(); k++) {
                r.put(hode.get(k), verdi(felt.get(k)));
            }
            rad(r, linje);
        }

        /** One row as a column → value map (null for empty), from a file line or an API object. */
        void rad(Map<String, String> r, int linje) {
            if (flagget) {
                flagget(r, linje);
            } else {
                eldre(r, linje);
            }
        }

        private void lesHode(List<String> felt) {
            hode = felt.stream().map(h -> h.replace(SITERT, "").strip().toLowerCase(Locale.ROOT)).toList();
            List<String> mangler = KOLONNER.stream().filter(k -> !hode.contains(k)).toList();
            if (!mangler.isEmpty()) {
                throw new Regelbrudd("Fila ser ikke ut som en eksport fra datavarehuset: mangler "
                        + String.join(", ", mangler) + ". " + Bruksdatafil.funnet(hode));
            }
            if (DATOKOLONNER.stream().noneMatch(hode::contains)) {
                throw new Regelbrudd("Eksporten fra datavarehuset mangler datokolonne (en av "
                        + String.join(", ", DATOKOLONNER) + "). " + Bruksdatafil.funnet(hode));
            }
            flagget = hode.contains(FLAGG_ALTINN);
            if (flagget && periode == null) {
                throw new IllegalArgumentException("En eksport med " + FLAGG_ALTINN + " kan bare leses for en periode");
            }
        }

        /** An older export: strict, every row is used (the aggregation rejects other months). */
        private void eldre(Map<String, String> r, int linje) {
            DwhRad rad = new DwhRad(
                    påkrevd(r, "organisation_number", linje),
                    påkrevd(r, "product_name", linje),
                    tall(r.get("quantity"), "quantity", linje),
                    tall(r.get("amount_nok"), "amount_nok", linje),
                    dato(r, linje),
                    r.get("kundenummer"),
                    r.get("fakturareferanse"),
                    null, null, null, null, null,
                    r.get("organisation_name"),
                    r.get("bestillingsnummer"));
            brukte.add(rad);
        }

        /** A flagged export: classify first, validate only what is used. */
        private void flagget(Map<String, String> r, int linje) {
            totalt++;
            Boolean altinn = flagg(r, FLAGG_ALTINN, true, linje);
            Boolean dpvFlagg = flagg(r, FLAGG_DPV, false, linje);
            if (altinn == null || dpvFlagg == null) {
                return;
            }
            LocalDate dato = dato(r, linje);
            String orgnr = r.get("organisation_number") == null ? null : r.get("organisation_number").strip();
            String produkt = r.get("product_name");
            BigDecimal antall = mild(r.get("quantity"));
            DwhRad rad = new DwhRad(orgnr, produkt, antall, mild(r.get("amount_nok")), dato,
                    r.get("kundenummer"), r.get("fakturareferanse"), altinn, dpvFlagg,
                    r.get("source_table"), ressurs(r.get("resource_id")), r.get("regel"),
                    r.get("organisation_name"), r.get("bestillingsnummer"));
            DwhRadklasse klasse = DwhRadklasse.klassifiser(rad, periode);
            switch (klasse) {
                case UTENFOR_PERIODE -> {
                    utenfor++;
                    utenforPerMaaned.merge(YearMonth.from(dato), 1, Integer::sum);
                }
                case DPV -> {
                    dpv++;
                    leggTilVolum(klasse, produkt, antall);
                }
                case EKSKLUDERT -> {
                    utelatt++;
                    leggTilVolum(klasse, produkt, antall);
                    arsaker.merge(arsak(rad.regel()), 1, Integer::sum);
                    String org = gyldigOrgnr(orgnr) ? orgnr : UKJENT_ORG;
                    utelattPerOrg.merge(org, antall == null ? BigDecimal.ZERO : antall, BigDecimal::add);
                }
                case BRUKT -> bruk(r, rad, linje);
            }
        }

        private void bruk(Map<String, String> r, DwhRad rad, int linje) {
            String ref = "linje " + linje;
            if (!gyldigOrgnr(rad.organisasjonsnummer())) {
                avviste.add(new AvvistRad(ref, "ugyldig organisasjonsnummer på rad merket for Altinn-fakturering: "
                        + rad.organisasjonsnummer()));
                return;
            }
            if (rad.produktnavn() == null || rad.produktnavn().isBlank()) {
                avviste.add(new AvvistRad(ref, "mangler product_name på rad merket for Altinn-fakturering"));
                return;
            }
            try {
                // Strict parse of what the pipeline will use; the lenient copy above would hide a typo.
                tall(r.get("quantity"), "quantity", linje);
                tall(r.get("amount_nok"), "amount_nok", linje);
            } catch (Regelbrudd e) {
                avviste.add(new AvvistRad(ref, e.getMessage()));
                return;
            }
            antallBrukt++;
            leggTilVolum(DwhRadklasse.BRUKT, rad.produktnavn(), rad.antall());
            brukte.add(rad);
        }

        private Boolean flagg(Map<String, String> r, String kolonne, boolean paakrevd, int linje) {
            if (!paakrevd && !hode.contains(kolonne)) {
                return Boolean.FALSE;
            }
            String v = r.get(kolonne) == null ? null : r.get(kolonne).strip();
            if ("1".equals(v)) {
                return Boolean.TRUE;
            }
            if ("0".equals(v)) {
                return Boolean.FALSE;
            }
            avviste.add(new AvvistRad("linje " + linje, "kolonne " + kolonne + ": "
                    + (v == null || v.isEmpty() ? "tom verdi" : "ugyldig verdi «" + v + "»")
                    + " (forventet 0 eller 1)"));
            return null;
        }

        private void leggTilVolum(DwhRadklasse klasse, String produkt, BigDecimal antall) {
            volum.computeIfAbsent(klasse, k -> new TreeMap<>())
                    .merge(produkt == null ? "(uten produktnavn)" : produkt,
                            antall == null ? BigDecimal.ZERO : antall, BigDecimal::add);
        }

        Fil ferdig() {
            if (hode == null) {
                throw new Regelbrudd("Fila er tom");
            }
            DwhFilrapport rapport = flagget
                    ? new DwhFilrapport(totalt, antallBrukt, utenfor, dpv, utelatt, avviste.size(),
                            utenforPerMaaned, volum, arsaker, utelattPerOrg)
                    : null;
            List<String> advarsler = !flagget && hode.contains(FLAGG_DPV)
                    ? List.of("Fila har " + FLAGG_DPV + " men ikke " + FLAGG_ALTINN
                            + ": den leses som en eksport uten flagg, og alle rader brukes.")
                    : List.of();
            return new Fil(flagget, brukte, avviste, rapport, advarsler);
        }
    }

    private static boolean gyldigOrgnr(String orgnr) {
        return orgnr != null && orgnr.matches("[0-9]{9}");
    }

    /** The resource id without the URN prefix, so both spellings group together. */
    static String ressurs(String ressurs) {
        if (ressurs == null) {
            return null;
        }
        String r = ressurs.strip();
        return r.startsWith(URN_PREFIKS) ? r.substring(URN_PREFIKS.length()) : r;
    }

    /** The file's own exclusion reason: its {@code ekskludert…} tokens, verbatim. Never interpreted. */
    static String arsak(String regel) {
        if (regel == null) {
            return "(uten regel)";
        }
        String arsak = Arrays.stream(regel.split(";"))
                .map(String::strip)
                .filter(t -> t.startsWith("ekskludert"))
                .collect(Collectors.joining("; "));
        return arsak.isEmpty() ? "(uten ekskludert-regel)" : arsak;
    }

    /** A number if it is one, else null — for rows that are left out anyway. */
    private static BigDecimal mild(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** RFC 4180: quoted fields may contain commas, doubled quotes and newlines. One callback per record. */
    private static void parse(String tekst, Consumer<List<String>> linje) {
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
                linje.accept(felt);
                felt = new ArrayList<>();
                sb.setLength(0);
                varSitert = false;
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0 || !felt.isEmpty()) {
            felt.add(markert(sb, varSitert));
            linje.accept(felt);
        }
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
