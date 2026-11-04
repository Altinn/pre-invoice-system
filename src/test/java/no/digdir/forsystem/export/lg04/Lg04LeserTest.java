package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.export.Lg04Eksport;
import no.digdir.forsystem.export.lg04.Lg04Ordre.Lg04Ordrelinje;
import org.junit.jupiter.api.Test;

/**
 * The independent LG04 reader (specs/002 D4): pinned by the golden file, round-trips the writer, and
 * reports every structural defect instead of returning a plausible total.
 */
class Lg04LeserTest {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private final Lg04Leser leser = new Lg04Leser();

    @Test
    void readsTheGoldenFile() throws IOException {
        var innhold = leser.les(gyllen("altinn-test3.txt"));

        assertThat(innhold.gyldig()).isTrue();
        assertThat(innhold.antallOrdrer()).isEqualTo(1);
        assertThat(innhold.sumOre()).isEqualTo(50365L + 465394L);   // the two products in Lg04SkriverTest
        assertThat(innhold.antallLinjer()).isEqualTo(5);
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
    void readsSeveralProductsPerOrder() {
        var innhold = leser.les(new Lg04Eksport().bygg(List.of(ordre(1, "100", "200", "300"), ordre(2, "5"))));

        assertThat(innhold.gyldig()).isTrue();
        assertThat(innhold.antallOrdrer()).isEqualTo(2);
        assertThat(innhold.sumOre()).isEqualTo(605L);
        assertThat(innhold.antallLinjer()).isEqualTo(1 + 6 + 1 + 2);
    }

    @Test
    void incompletePairIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.remove(2);
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("mangler den andre linjen"));
    }

    @Test
    void headerWithoutProductsIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        linjer.add(linjer(ordre(2, "100")).get(0));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("ingen produktlinjer"));
    }

    @Test
    void productNumbersOutOfSequenceIsAnError() {
        List<String> linjer = linjer(ordre(1, "100", "200"));
        linjer.set(3, sett(linjer.get(3), 2493, "3"));
        linjer.set(4, sett(linjer.get(4), 2493, "3"));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("feil linjenummer"));
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
        linjer.set(1, sett(linjer.get(1), 965, "                 999"));
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("beløpsfeltene er ulike"));
    }

    @Test
    void nonNumericAmountIsAnError() {
        List<String> linjer = linjer(ordre(1, "100"));
        String belop = sett(sett(linjer.get(1), 212, "1x0"), 965, "1x0");
        linjer.set(1, belop);
        assertThat(leser.les(fil(linjer)).feil()).anySatisfy(f -> assertThat(f).contains("ikke et gyldig tall"));
    }

    private static Lg04Ordre ordre(int nr, String... ore) {
        return new Lg04Ordre("KN1", "ref", "best", "Bruk av Altinn januar 2027", nr,
                "https://example.invalid/x.pdf", "a", "b", "20270206",
                Arrays.stream(ore).map(o -> new Lg04Ordrelinje("3030", "20002", "7130", "4206", "454005",
                        "Melding", o, BigDecimal.TEN)).toList());
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

    private byte[] gyllen(String navn) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lg04/" + navn)) {
            return in.readAllBytes();
        }
    }
}
