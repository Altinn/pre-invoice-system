package no.digdir.forsystem.usage;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import no.digdir.forsystem.common.Regelbrudd;

/**
 * Recognises which of the two accepted usage files an upload is, from its header row: forsystem's own
 * CSV format (docs/csv-format.md) or the datavarehus's export of {@code mv_altinn_usage_monthly}. One
 * upload field takes both, so the user never has to know the difference. A file that is neither is
 * refused with the columns it actually has, plus a hint for the usual causes (re-saved in Excel).
 *
 * <p>Both formats are UTF-8. A file that is not — typically one saved from Excel as «ANSI»
 * (Windows-1252) — is refused, as is one whose æøå were already converted wrongly once («Ã¸» for «ø»).
 * Read leniently, the first would put «�» in names and references, and the second would make a
 * 25-character reference look like 26.
 */
public final class Bruksdatafil {

    public enum Format { FORSYSTEM, DATAVAREHUS }

    private Bruksdatafil() {
    }

    public static Format gjenkjenn(byte[] innhold) {
        if (innhold.length >= 4 && innhold[0] == 'P' && innhold[1] == 'K' && innhold[2] == 3 && innhold[3] == 4) {
            throw new Regelbrudd("Fila er et Excel-regneark, ikke CSV. Last opp den opprinnelige CSV-fila.");
        }
        String tekst = utf8(innhold);
        String hode = forsteLinje(tekst);
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

    /**
     * UTF-8 as two bytes read as Windows-1252 (or Latin-1): «Ã» followed by the second byte of æøå, ÆØÅ,
     * é, ä, ö or ü. No Norwegian name or reference contains these pairs.
     */
    private static final Pattern FEILKONVERTERT = Pattern.compile("Ã[¦¸¥†˜…©¤¶¼\u0085\u0086\u0098]");

    private static final String EXCEL_HINT = "Det skjer gjerne når fila er åpnet og lagret i Excel. "
            + "Last opp fila slik den kom fra datavarehuset, eller lagre den som «CSV UTF-8».";

    /** The file as text, refused if it is not UTF-8 or carries æøå that were converted wrongly once. */
    static String utf8(byte[] innhold) {
        CharsetDecoder dekoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer inn = ByteBuffer.wrap(innhold);
        CharBuffer ut = CharBuffer.allocate(innhold.length + 1);
        CoderResult r = dekoder.decode(inn, ut, true);
        if (!r.isError()) {
            r = dekoder.flush(ut);
        }
        if (r.isError()) {
            throw new Regelbrudd("Fila er ikke lagret som UTF-8 (linje " + linje(innhold, inn.position())
                    + "), så æ, ø og å ville blitt feil. " + EXCEL_HINT);
        }
        String tekst = ut.flip().toString();
        Matcher m = FEILKONVERTERT.matcher(tekst);
        if (m.find()) {
            long linje = tekst.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
            throw new Regelbrudd("Fila har æ, ø eller å som er konvertert feil («" + m.group()
                    + "», linje " + linje + "). " + EXCEL_HINT);
        }
        return tekst;
    }

    private static int linje(byte[] innhold, int posisjon) {
        int linje = 1;
        for (int i = 0; i < posisjon; i++) {
            if (innhold[i] == '\n') {
                linje++;
            }
        }
        return linje;
    }

    private static String forsteLinje(String tekst) {
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
