package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;

/** The Data API Builder response contract (specs/001-dwh-usage-import/contracts/dwh-api.md). */
class DwhResponsTest {

    @Test
    void parsesFixture() throws IOException {
        var rader = DwhRespons.les(fixture("januar-ok.json"));
        assertThat(rader).hasSize(6);
        assertThat(rader.getFirst()).satisfies(r -> {
            assertThat(r.organisasjonsnummer()).isEqualTo("123456789");
            assertThat(r.produktnavn()).isEqualTo("Formidling");
            assertThat(r.antall()).isEqualByComparingTo("100");
            assertThat(r.belop()).isNull();
            assertThat(r.dato()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(r.kundenummer()).isEqualTo("1234");
            assertThat(r.fakturareferanse()).isEqualTo("TESTREF1");
        });
    }

    @Test
    void nextLinkMeansIncompleteAndIsRefused() {
        assertThatThrownBy(() -> DwhRespons.les(fixture("med-nextlink.json")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Ufullstendig svar");
    }

    @Test
    void nonJsonIsRefused() {
        assertThatThrownBy(() -> DwhRespons.les("<html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("ikke gyldig JSON");
    }

    @Test
    void missingValueListIsRefused() {
        assertThatThrownBy(() -> DwhRespons.les("{\"error\":{}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("value");
    }

    @Test
    void missingRequiredFieldIsRefused() {
        String json = "{\"value\":[{\"organisation_number\":\"123456789\",\"quantity\":1,"
                + "\"transaction_date\":\"2027-01-01\"}]}";
        assertThatThrownBy(() -> DwhRespons.les(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("product_name");
    }

    @Test
    void nonNumericQuantityIsRefused() {
        String json = "{\"value\":[{\"organisation_number\":\"123456789\",\"product_name\":\"Formidling\","
                + "\"quantity\":\"mye\",\"transaction_date\":\"2027-01-01\"}]}";
        assertThatThrownBy(() -> DwhRespons.les(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("quantity");
    }

    @Test
    void unknownFieldsAreTolerated() {
        String json = "{\"value\":[{\"organisation_number\":\"123456789\",\"product_name\":\"Formidling\","
                + "\"quantity\":1,\"transaction_date\":\"2027-01-01T00:00:00\",\"nytt_felt\":42}]}";
        assertThat(DwhRespons.les(json.getBytes(StandardCharsets.UTF_8))).singleElement()
                .satisfies(r -> assertThat(r.dato()).isEqualTo(LocalDate.of(2027, 1, 1)));
    }

    static byte[] fixture(String navn) throws IOException {
        try (var inn = DwhResponsTest.class.getResourceAsStream("/dwh/" + navn)) {
            return inn.readAllBytes();
        }
    }
}
