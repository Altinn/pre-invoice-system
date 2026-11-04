package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.export.Lg04Eksport;
import no.digdir.forsystem.export.lg04.Lg04Ordre.Lg04Ordrelinje;
import org.junit.jupiter.api.Test;

/**
 * Multi-order assembly: each order is a header plus one line pair per product. Proves the line count,
 * that every line is 4324 chars, and that per-product amounts and line numbers land at the right offsets.
 */
class Lg04EksportUnitTest {

    private final Lg04Eksport eksport = new Lg04Eksport();

    private Lg04Ordre ordre(int nr, String... ore) {
        return new Lg04Ordre("KN" + nr, "ref", "best", "Bruk av Altinn januar 2027", nr, "url", "A", "B",
                "20270206", Arrays.stream(ore).map(o -> new Lg04Ordrelinje("3030", "20002", "7130", "4206",
                        "454005", "Melding", o, BigDecimal.ONE)).toList());
    }

    @Test
    void ordersProduceAHeaderAndAPairPerProduct() {
        String tekst = eksport.byggTekst(List.of(ordre(1, "500", "700"), ordre(2, "12345")));
        List<String> linjer = Arrays.stream(tekst.split("\n", -1)).filter(l -> !l.isEmpty()).toList();

        assertThat(linjer).hasSize(5 + 3);
        assertThat(linjer).allSatisfy(l -> assertThat(l).hasSize(Lg04Skriver.LINJELENGDE));
        assertThat(linjer).extracting(l -> l.charAt(2493)).containsExactly('0', '1', '1', '2', '2', '0', '1', '1');
        // Amount (øre) sits at offset 212 on each product's first line.
        assertThat(linjer.get(1).substring(212, 232).trim()).isEqualTo("500");
        assertThat(linjer.get(3).substring(212, 232).trim()).isEqualTo("700");
        assertThat(linjer.get(6).substring(212, 232).trim()).isEqualTo("12345");
    }
}
