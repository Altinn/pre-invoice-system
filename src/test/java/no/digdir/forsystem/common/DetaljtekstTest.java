package no.digdir.forsystem.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The jsonb details of an audit row as readable text (specs/014). */
class DetaljtekstTest {

    @Test
    void flatObjectReadsAsKeyValuePairs() {
        assertThat(Detaljtekst.les(Jsonb.of("{\"periode\": \"2027-01\", \"antallFakturaer\": 7}")))
                .isEqualTo("periode: 2027-01; antallFakturaer: 7");
    }

    @Test
    void nestedObjectsGetDottedNamesAndListsAreCommaSeparated() {
        String json = "{\"endringer\": {\"topptekst\": {\"fra\": \"A\", \"til\": \"B\"}}, \"funnIder\": [3, 4],"
                + " \"tom\": {}, \"ingenting\": null, \"liste\": [{\"a\": 1}, {\"b\": \"x\"}]}";
        assertThat(Detaljtekst.les(Jsonb.of(json))).isEqualTo(
                "endringer.topptekst.fra: A; endringer.topptekst.til: B; funnIder: 3, 4; tom: ; ingenting: ;"
                        + " liste: {a: 1}, {b: x}");
    }

    @Test
    void noDetailsIsEmptyAndInvalidJsonIsShownAsIs() {
        assertThat(Detaljtekst.les(null)).isEmpty();
        assertThat(Detaljtekst.les(Jsonb.of(" "))).isEmpty();
        assertThat(Detaljtekst.les(Jsonb.of("ikke json {"))).isEqualTo("ikke json {");
        assertThat(Detaljtekst.les(Jsonb.of("\"tekst\""))).isEqualTo("tekst");
    }

    @Test
    void periodeIsLiftedAsYearMonth() {
        assertThat(Detaljtekst.periode(Jsonb.of("{\"periode\": \"2027-01-01\"}"))).isEqualTo("2027-01");
        assertThat(Detaljtekst.periode(Jsonb.of("{\"periode\": \"2027-01\"}"))).isEqualTo("2027-01");
        assertThat(Detaljtekst.periode(Jsonb.of("{\"navn\": \"x\"}"))).isEmpty();
        assertThat(Detaljtekst.periode(Jsonb.of("{\"periode\": 5}"))).isEmpty();
        assertThat(Detaljtekst.periode(null)).isEmpty();
    }
}
