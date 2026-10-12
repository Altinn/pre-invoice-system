package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import no.digdir.forsystem.export.Lg04Eksport;
import org.junit.jupiter.api.Test;

/**
 * The independent LG04 reader (specs/002 D4): pinned by the golden files, round-trips the writer, and
 * reports every structural defect instead of returning a plausible total.
 */
class Lg04LeserTest {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private final Lg04Leser leser = new Lg04Leser();

    @Test
    void readsTheGoldenOrder() throws IOException {
        String fil = gyllen("linje1.txt") + "\n" + gyllen("linje2.txt") + "\n" + gyllen("linje3.txt") + "\n";

        var innhold = leser.les(fil.getBytes(WIN_1252));

        assertThat(innhold.gyldig()).isTrue();
        assertThat(innhold.antallOrdrer()).isEqualTo(1);
        assertThat(innhold.sumOre()).isEqualTo(2810L);   // the reference order's amount (Lg04SkriverTest)
        assertThat(innhold.antallLinjer()).isEqualTo(3);
    }

    @Test
    void roundTripsWhatTheWriterProduces() {
        List<Lg04Ordre> ordrer = List.of(ordre(1, "85000"), ordre(2, "0"), ordre(3, "-1250"),
                ordre(4, "25050"), ordre(5, "1"));

        var innhold = leser.les(new Lg04Eksport().bygg(ordrer));

        assertThat(innhold.gyldig()).isTrue();
        assertThat(innhold.antallOrdrer()).isEqualTo(5);
        assertThat(innhold.sumOre()).isEqualTo(85000L + 0 - 1250 + 25050 + 1);
    }

    @Test
    void emptyFileIsZeroOrders() {
        var innhold = leser.les(new byte[0]);
        assertThat(innhold.gyldig()).isTrue();
        assertThat(innhold.antallOrdrer()).isZero();
        assertThat(innhold.sumOre()).isZero();
    }

    @Test
    void shortLineIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.set(1, linjer.get(1).substring(0, 4000));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("4000 tegn"));
    }

    @Test
    void lineCountNotMultipleOfThreeIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.remove(2);
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("multiplum av 3"));
    }

    @Test
    void wrongLineTypeOrderIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        String hode = linjer.get(0);
        linjer.set(0, linjer.get(1));
        linjer.set(1, hode);
        var innhold = leser.les(fil(linjer));
        assertThat(innhold.gyldig()).isFalse();
        assertThat(innhold.antallOrdrer()).isZero();
    }

    @Test
    void mismatchedOrderNumbersWithinATripleIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.set(2, linjer(ordre(2, "100")).get(2));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("ordrenummeret"));
    }

    @Test
    void disagreeingAmountFieldsIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.set(1, sett(linjer.get(1), 965, "999                 "));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("beløpsfeltene er ulike"));
    }

    @Test
    void nonNumericAmountIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        String belop = sett(sett(linjer.get(1), 212, "1x0"), 965, "1x0");
        linjer.set(1, belop);
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("ikke et gyldig tall"));
    }

    private static Lg04Ordre ordre(int nr, String ore) {
        return new Lg04Ordre("3030", "20002", "7130", "4206", "454005", "KN1", "ref", "best",
                "Bruk av melding januar 2027", ore, nr, "https://example.invalid/x.pdf", "a", "b", "20270206", nr);
    }

    private static List<String> linjer(Lg04Ordre o) {
        String tekst = new Lg04Eksport().byggTekst(List.of(o));
        List<String> l = new ArrayList<>(List.of(tekst.split("\n")));
        return l;
    }

    private static byte[] fil(List<String> linjer) {
        return (String.join("\n", linjer) + "\n").getBytes(WIN_1252);
    }

    private static String sett(String linje, int fra, String verdi) {
        return linje.substring(0, fra) + verdi + linje.substring(fra + verdi.length());
    }

    private String gyllen(String navn) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lg04/" + navn)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
