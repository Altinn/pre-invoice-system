package no.digdir.forsystem.web.hjelp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * In-memory search over the learning center (specs/016 research R9). About 45 articles, so a linear scan: every
 * query word must match, and hits rank title, then keywords, then summary, then body. Text is lower-cased and
 * accent-folded, but æ, ø and å stay letters.
 */
@Component
public class Hjelpesok {

    static final int MAKS_TREFF = 20;
    private static final Pattern ORDGRENSE = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** One search hit. */
    public record Treff(Artikkel artikkel, int poeng) {}

    /** On equal score, how-to articles come before explanations, reference and troubleshooting. */
    private static final List<String> SEKSJONSRANG = List.of(
            "slik-gjor-du", "manedsrutinen", "forsta-systemet", "oppslag", "kom-i-gang", "nar-noe-gar-galt");

    private record Indeks(Artikkel artikkel, String tittel, String nokkelord, String sammendrag, String tekst) {}

    private final List<Indeks> indeks;

    public Hjelpesok(Hjelpebibliotek bibliotek) {
        this.indeks = bibliotek.alle().stream()
                .map(a -> new Indeks(a, normaliser(a.tittel()), normaliser(String.join(" ", a.nokkelord())),
                        normaliser(a.sammendrag()), normaliser(a.tekst())))
                .toList();
    }

    public List<Treff> sok(String sporring) {
        List<String> ord = Arrays.stream(ORDGRENSE.split(normaliser(sporring == null ? "" : sporring)))
                .filter(o -> !o.isBlank()).distinct().toList();
        if (ord.isEmpty()) {
            return List.of();
        }
        List<Treff> treff = new ArrayList<>();
        for (Indeks i : indeks) {
            int sum = 0;
            boolean alle = true;
            for (String o : ord) {
                int p = poeng(i, o);
                if (p == 0) {
                    alle = false;
                    break;
                }
                sum += p;
            }
            if (alle) {
                if (i.tittel().equals(String.join(" ", ord))) {
                    sum += 50; // the title is exactly what was typed
                }
                treff.add(new Treff(i.artikkel(), sum));
            }
        }
        return treff.stream()
                .sorted(Comparator.comparingInt(Treff::poeng).reversed()
                        .thenComparingInt(t -> {
                            int rang = SEKSJONSRANG.indexOf(t.artikkel().seksjon());
                            return rang < 0 ? SEKSJONSRANG.size() : rang;
                        })
                        .thenComparing(t -> t.artikkel().tittel().length())
                        .thenComparing(t -> t.artikkel().id()))
                .limit(MAKS_TREFF)
                .toList();
    }

    private static int poeng(Indeks i, String ord) {
        int p = 0;
        if (i.tittel().contains(ord)) {
            p += 100;
            if (Arrays.asList(ORDGRENSE.split(i.tittel())).contains(ord)) {
                p += 20; // the whole word, not just part of a longer one
            }
        }
        if (i.nokkelord().contains(ord)) {
            p += 50;
        }
        if (i.sammendrag().contains(ord)) {
            p += 10;
        }
        if (i.tekst().contains(ord)) {
            p += 1;
        }
        return p;
    }

    /** Lower-case, strip accents (é → e) but keep å (a with ring), æ and ø as their own letters. */
    static String normaliser(String s) {
        String d = Normalizer.normalize(s.toLowerCase(Locale.forLanguageTag("nb")), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(d.length());
        for (int k = 0; k < d.length(); k++) {
            char c = d.charAt(k);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                if (c == '̊' && k > 0 && d.charAt(k - 1) == 'a') {
                    sb.append(c);
                }
                continue;
            }
            sb.append(c);
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC);
    }
}
