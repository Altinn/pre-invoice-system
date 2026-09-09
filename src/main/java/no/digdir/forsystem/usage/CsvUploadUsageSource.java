package no.digdir.forsystem.usage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Component;

/**
 * CSV upload adapter for {@link UsageDataSource}. Parses the documented comma-separated format
 * (see {@code docs/csv-format.md}) into raw rows; all semantic validation happens in
 * {@link UsageImportService}. UTF-8, dot as decimal separator, one header row.
 */
@Component
class CsvUploadUsageSource implements UsageDataSource {

    static final List<String> KOLONNER =
            List.of("periode", "organisasjonsnummer", "produktkode", "type", "antall", "belop");

    @Override
    public Kilde kilde() {
        return Kilde.CSV;
    }

    @Override
    public List<RaaBruksrad> lesRader(Periode periode, InputStream innhold) throws IOException {
        List<RaaBruksrad> rader = new ArrayList<>();
        try (var leser = new BufferedReader(new InputStreamReader(innhold, StandardCharsets.UTF_8))) {
            String linje;
            int linjenr = 0;
            Map<String, Integer> kolonneIndeks = null;
            while ((linje = leser.readLine()) != null) {
                linjenr++;
                if (linjenr == 1) {
                    linje = fjernBom(linje);
                }
                if (linje.isBlank()) {
                    continue;
                }
                String[] felt = splitt(linje);
                if (kolonneIndeks == null) {
                    kolonneIndeks = lesHode(felt);
                    continue;
                }
                rader.add(new RaaBruksrad(
                        linjenr,
                        hent(felt, kolonneIndeks, "periode"),
                        hent(felt, kolonneIndeks, "organisasjonsnummer"),
                        hent(felt, kolonneIndeks, "produktkode"),
                        hent(felt, kolonneIndeks, "type"),
                        hent(felt, kolonneIndeks, "antall"),
                        hent(felt, kolonneIndeks, "belop")));
            }
            if (kolonneIndeks == null) {
                throw new Regelbrudd("CSV-filen er tom eller mangler kolonneoverskrifter");
            }
        }
        return rader;
    }

    private static Map<String, Integer> lesHode(String[] felt) {
        Map<String, Integer> indeks = new HashMap<>();
        for (int i = 0; i < felt.length; i++) {
            indeks.put(felt[i].trim().toLowerCase(), i);
        }
        List<String> mangler = KOLONNER.stream().filter(k -> !indeks.containsKey(k)).toList();
        if (!mangler.isEmpty()) {
            throw new Regelbrudd("CSV mangler kolonner: " + String.join(", ", mangler));
        }
        return indeks;
    }

    private static String hent(String[] felt, Map<String, Integer> indeks, String kolonne) {
        int i = indeks.get(kolonne);
        if (i >= felt.length) {
            return null;
        }
        String v = felt[i].trim();
        return v.isEmpty() ? null : v;
    }

    private static String[] splitt(String linje) {
        // Documented format has no embedded commas/quotes; a simple split is sufficient and explicit.
        return linje.split(",", -1);
    }

    private static String fjernBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }
}
