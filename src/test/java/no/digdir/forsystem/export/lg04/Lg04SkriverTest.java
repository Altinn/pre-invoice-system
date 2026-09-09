package no.digdir.forsystem.export.lg04;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Golden-file tests for the LG04 writer. The expected lines are extracted from the reference
 * system's Linje1/2/3 tests (../agresso) with the batch_id token preserved; person names in the
 * fixture are anonymized with same-length placeholders, so a matching render still proves
 * byte-identical output for the single-line case (docs/04, docs/05).
 */
class Lg04SkriverTest {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private final Lg04Skriver skriver = new Lg04Skriver();

    private Lg04Ordre referanseordre() {
        return new Lg04Ordre(
                "3030", "20002", "7130", "4206", "454005",
                "1967", "Karin Nordmann", "28. okt.",
                "Bruk av digital postkasse 2. kvartal 2026", "2810",
                13, "https://faktura.digdir.no/detaljer/1967-e0fe9759-a306-46cb-8d73-54e7bca5925d.xlsx",
                "Ola Nordmann", "Ola Nordmann", "batch_id", 8);
    }

    @Test
    void linje1IsByteIdenticalToReference() throws IOException {
        assertThat(skriver.renderLinje1(referanseordre())).isEqualTo(gyllen("linje1.txt"));
    }

    @Test
    void linje2IsByteIdenticalToReference() throws IOException {
        assertThat(skriver.renderLinje2(referanseordre())).isEqualTo(gyllen("linje2.txt"));
    }

    @Test
    void linje3IsByteIdenticalToReference() throws IOException {
        assertThat(skriver.renderLinje3(referanseordre())).isEqualTo(gyllen("linje3.txt"));
    }

    @Test
    void everyLineIsExactly4324Characters() {
        Lg04Ordre o = referanseordre();
        assertThat(skriver.renderLinje1(o)).hasSize(Lg04Skriver.LINJELENGDE);
        assertThat(skriver.renderLinje2(o)).hasSize(Lg04Skriver.LINJELENGDE);
        assertThat(skriver.renderLinje3(o)).hasSize(Lg04Skriver.LINJELENGDE);
    }

    @Test
    void fieldOffsetSpotChecks() {
        String l1 = skriver.renderLinje1(referanseordre());
        assertThat(l1.substring(25, 39)).isEqualTo("Karin Nordmann");   // accountable
        assertThat(l1.substring(233, 237)).isEqualTo("1967");           // apar_id
        assertThat(l1.charAt(2493)).isEqualTo('0');                     // line_no
        String l2 = skriver.renderLinje2(referanseordre());
        assertThat(l2.substring(0, 4)).isEqualTo("3030");              // account
        assertThat(l2.substring(212, 216)).isEqualTo("2810");          // amount (øre)
        assertThat(l2.charAt(2493)).isEqualTo('1');
    }

    @Test
    void windows1252EncodesNorwegianLettersAsSingleBytes() {
        Lg04Ordre o = new Lg04Ordre("3030", "20002", "7130", "4206", "454005",
                "1", "ref", "", "Bruk av melding æ ø å", "100", 1, "u", "a", "b", "batch_id", 1);
        String linje = skriver.renderLinje2(o);
        byte[] bytes = linje.getBytes(WIN_1252);
        // Windows-1252 is single-byte, so the byte length equals the character length.
        assertThat(bytes).hasSize(Lg04Skriver.LINJELENGDE);
        // æ=0xE6, ø=0xF8, å=0xE5 as documented in docs/02.
        String reEncoded = new String(bytes, WIN_1252);
        assertThat(reEncoded).contains("Bruk av melding æ ø å");
        assertThat((bytes[linje.indexOf('å')] & 0xFF)).isEqualTo(0xE5);
        assertThat((bytes[linje.indexOf('æ')] & 0xFF)).isEqualTo(0xE6);
        assertThat((bytes[linje.indexOf('ø')] & 0xFF)).isEqualTo(0xF8);
    }

    private String gyllen(String navn) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/lg04/" + navn)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
