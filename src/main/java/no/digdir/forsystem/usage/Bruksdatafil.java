package no.digdir.forsystem.usage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import no.digdir.forsystem.common.Regelbrudd;

/**
 * Recognises which of the two accepted usage files an upload is, from its header row: forsystem's own
 * CSV format (docs/csv-format.md) or the datavarehus's export of {@code mv_altinn_usage_monthly}. One
 * upload field takes both, so the user never has to know the difference. A file that is neither is
 * refused with the columns it actually has, plus a hint for the usual causes (re-saved in Excel).
 */
public final class Bruksdatafil {

    public enum Format { FORSYSTEM, DATAVAREHUS }

    private Bruksdatafil() {
    }

    public static Format gjenkjenn(byte[] innhold) {
        if (innhold.length >= 4 && innhold[0] == 'P' && innhold[1] == 'K' && innhold[2] == 3 && innhold[3] == 4) {
            throw new Regelbrudd("Fila er et Excel-regneark, ikke CSV. Last opp den opprinnelige CSV-fila.");
        }
        String hode = forsteLinje(innhold);
        if (hode.isBlank()) {
            throw new Regelbrudd("Fila er tom");
        }
        List<String> kolonner = kolonner(hode);
        if (kolonner.contains("organisation_number")) {
            return Format.DATAVAREHUS;
        }
        if (kolonner.contains("organisasjonsnummer")) {
            return Format.FORSYSTEM;
        }
        String semikolon = kolonner.size() == 1 && hode.contains(";")
                ? "Fila er delt med semikolon, ikke komma. Det skjer når den er åpnet og lagret i Excel. "
                + "Last opp den opprinnelige fila. "
                : "";
        throw new Regelbrudd(semikolon + "Kjenner ikke igjen formatet på fila. " + funnet(kolonner)
                + " Forventet enten forsystemets format (" + String.join(", ", CsvUploadUsageSource.KOLONNER)
                + ") eller eksporten fra datavarehuset (" + String.join(", ", DwhCsv.KOLONNER)
                + " og en datokolonne: " + String.join(", ", DwhCsv.DATOKOLONNER) + ").");
    }

    /** "Kolonner i fila: a, b, c." — appended to every column error so the user sees what was found. */
    static String funnet(List<String> kolonner) {
        return "Kolonner i fila: " + String.join(", ", kolonner) + ".";
    }

    private static String forsteLinje(byte[] innhold) {
        String tekst = new String(innhold, StandardCharsets.UTF_8);
        if (tekst.startsWith("﻿")) {
            tekst = tekst.substring(1);
        }
        return tekst.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
    }

    private static List<String> kolonner(String hode) {
        return Arrays.stream(hode.split(",", -1))
                .map(k -> k.replace("\"", "").strip().toLowerCase(Locale.ROOT))
                .toList();
    }
}
