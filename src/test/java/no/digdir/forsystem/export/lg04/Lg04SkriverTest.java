package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import no.digdir.forsystem.export.Lg04Eksport;
import no.digdir.forsystem.export.lg04.Lg04Ordre.Lg04Ordrelinje;
import org.junit.jupiter.api.Test;

/**
 * Golden-file tests for the LG04 writer. {@code altinn-test3.txt} is FEL's Altinn sample (test_3,
 * 2026-11) — one customer, two products — with the person name anonymized to a same-length
 * placeholder, so a matching render still proves byte-identical output for the whole file (docs/05).
 */
class Lg04SkriverTest {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private final Lg04Skriver skriver = new Lg04Skriver();

    static Lg04Ordre referanseordre() {
        return new Lg04Ordre(
                "5559", "Ola Nordmann", "Ola Nordmann", "Bruk av Altinn september 2026", 19,
                "https://faktura.digdir.no/samarbeidsportalen (endre fakturainformasjon)",
                "servicedesk@altinn.no", "servicedesk@altinn.no", "20261016",
                List.of(new Lg04Ordrelinje("3030", "20010", "7130", "4206", "454005",
                                "Altinn studio", "50365", new BigDecimal("152")),
                        new Lg04Ordrelinje("3030", "20011", "7130", "4206", "454005",
                                "Autorisasjon", "465394", new BigDecimal("69984"))));
    }

    @Test
    void fileIsByteIdenticalToFelsSample() throws IOException {
        assertThat(new Lg04Eksport().bygg(List.of(referanseordre()))).isEqualTo(gyllen("altinn-test3.txt"));
    }

    @Test
    void everyLineIsExactly4324Characters() {
        Lg04Ordre o = referanseordre();
        assertThat(skriver.renderLinje1(o)).hasSize(Lg04Skriver.LINJELENGDE);
        assertThat(skriver.renderLinje2(o, 1)).hasSize(Lg04Skriver.LINJELENGDE);
        assertThat(skriver.renderLinje3(o, 2)).hasSize(Lg04Skriver.LINJELENGDE);
    }

    @Test
    void fieldOffsetSpotChecks() {
        Lg04Ordre o = referanseordre();
        String l1 = skriver.renderLinje1(o);
        assertThat(l1.substring(233, 237)).isEqualTo("5559");                   // apar_id
        assertThat(l1.charAt(2493)).isEqualTo('0');                             // line_no
        String l2 = skriver.renderLinje2(o, 2);
        assertThat(l2.substring(0, 4)).isEqualTo("3030");                      // account
        assertThat(l2.substring(212, 232)).isEqualTo("              465394");  // amount (øre), right-aligned
        assertThat(l2.substring(538, 550)).isEqualTo("Autorisasjon");          // art_desc = product name
        assertThat(l2.charAt(2493)).isEqualTo('2');                            // line_no counts per product
        assertThat(l2.substring(4224, 4244).trim()).isEqualTo("6998400");      // value_1 = antall × 100
        assertThat(skriver.renderLinje3(o, 2).charAt(3314)).isEqualTo('8');    // fixed sequence number
    }

    @Test
    void value1IsBlankWithoutQuantity() {
        // Blank means 1.0 in Unit4 (LG04_55 value_1); FEL's -100 is only FEL's own import-error marker.
        assertThat(skriver.renderLinje2(ordreMedAntall(null), 1).substring(4224, 4244)).isBlank();
    }

    @Test
    void value1IsZeroForZeroQuantity() {
        assertThat(skriver.renderLinje2(ordreMedAntall(BigDecimal.ZERO), 1).substring(4224, 4244))
                .isEqualTo("                   0");
    }

    @Test
    void value1RoundsToHundredths() {
        assertThat(skriver.renderLinje2(ordreMedAntall(new BigDecimal("2.5")), 1).substring(4224, 4244).trim())
                .isEqualTo("250");
        assertThat(skriver.renderLinje2(ordreMedAntall(new BigDecimal("1.005")), 1).substring(4224, 4244).trim())
                .isEqualTo("101");
    }

    @Test
    void headerAndFooterTextAreCutAt120() {
        String lang = "x".repeat(118) + "AB" + "overflow!!";
        Lg04Ordre o = new Lg04Ordre("1", "ref", "", lang, 1, lang, "a", "b", "20270206",
                List.of(new Lg04Ordrelinje("3030", "20010", null, null, null, "Melding", "100", BigDecimal.ONE)));
        for (String linje : List.of(skriver.renderLinje1(o), skriver.renderLinje2(o, 1), skriver.renderLinje3(o, 1))) {
            assertThat(linje.substring(2509, 2629)).isEqualTo(lang.substring(0, 120));
            assertThat(linje.substring(2629, 2749)).isEqualTo(lang.substring(0, 120));
            assertThat(linje.charAt(2749)).isEqualTo(' ');
        }
    }

    private static Lg04Ordre ordreMedAntall(BigDecimal antall) {
        return new Lg04Ordre("1", "ref", "", "t", 1, "u", "a", "b", "20270206",
                List.of(new Lg04Ordrelinje("3030", "20009", null, null, null, "Applikasjonsinfrastruktur",
                        "12345", antall)));
    }

    @Test
    void windows1252EncodesNorwegianLettersAsSingleBytes() {
        Lg04Ordre o = new Lg04Ordre("1", "ref", "", "Bruk av Altinn januar 2027", 1, "u", "a", "b", "20270206",
                List.of(new Lg04Ordrelinje("3030", "20002", "7130", "4206", "454005", "Melding æ ø å", "100",
                        BigDecimal.ONE)));
        String linje = skriver.renderLinje2(o, 1);
        byte[] bytes = linje.getBytes(WIN_1252);
        // Windows-1252 is single-byte, so the byte length equals the character length.
        assertThat(bytes).hasSize(Lg04Skriver.LINJELENGDE);
        // æ=0xE6, ø=0xF8, å=0xE5 as documented in docs/02.
        String reEncoded = new String(bytes, WIN_1252);
        assertThat(reEncoded).contains("Melding æ ø å");
        assertThat((bytes[linje.indexOf('å')] & 0xFF)).isEqualTo(0xE5);
        assertThat((bytes[linje.indexOf('æ')] & 0xFF)).isEqualTo(0xE6);
        assertThat((bytes[linje.indexOf('ø')] & 0xFF)).isEqualTo(0xF8);
    }

    private byte[] gyllen(String navn) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lg04/" + navn)) {
            return in.readAllBytes();
        }
    }
}
