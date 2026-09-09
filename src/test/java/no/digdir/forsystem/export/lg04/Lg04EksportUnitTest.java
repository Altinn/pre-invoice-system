package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.export.Lg04Eksport;
import org.junit.jupiter.api.Test;

/**
 * Multi-order assembly (the flag's default: one LG04 order per invoice line). Proves N orders →
 * 3N lines, each 4324 chars, with per-order amounts at the right offset.
 */
class Lg04EksportUnitTest {

    private final Lg04Eksport eksport = new Lg04Eksport();

    private Lg04Ordre ordre(int nr, String ore) {
        return new Lg04Ordre("3030", "20002", "7130", "4206", "454005", "KN" + nr, "ref", "best",
                "Bruk av melding januar 2027", ore, nr, "url", "A", "B", "20270201", nr);
    }

    @Test
    void twoOrdersProduceSixLinesEach4324WithCorrectAmounts() {
        String tekst = eksport.byggTekst(List.of(ordre(1, "500"), ordre(2, "12345")));
        List<String> linjer = Arrays.stream(tekst.split("\n", -1)).filter(l -> !l.isEmpty()).toList();

        assertThat(linjer).hasSize(6);
        assertThat(linjer).allSatisfy(l -> assertThat(l).hasSize(Lg04Skriver.LINJELENGDE));
        // Amount (øre) sits at offset 212 on each order's line 2 (indices 1 and 4).
        assertThat(linjer.get(1).substring(212, 232).trim()).isEqualTo("500");
        assertThat(linjer.get(4).substring(212, 232).trim()).isEqualTo("12345");
    }
}
