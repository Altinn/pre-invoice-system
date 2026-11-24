package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import no.digdir.forsystem.common.Periode;
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
            assertThat(r.navn()).isEqualTo("testetat");          // specs/018: goes on the invoice
            assertThat(r.bestillingsnummer()).isNull();          // null in the view today
        });
    }

    @Test
    void customerFieldsAreOptional() {
        var rader = DwhRespons.les(("{\"value\":[{\"organisation_number\":\"123456789\",\"product_name\":\"Formidling\","
                + "\"quantity\":1,\"amount_nok\":null,\"transaction_date\":\"2027-01-01\"}]}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(rader).singleElement().satisfies(r -> {
            assertThat(r.navn()).isNull();
            assertThat(r.kundenummer()).isNull();
            assertThat(r.bestillingsnummer()).isNull();
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

    /** The API now serves the flagged export (OQ-25): excluded rows, even without an orgnr, are never an error. */
    @Test
    void flaggedResponseIsClassifiedLikeAFile() {
        String json = "{\"value\":["
                + row("", "Altinn studio", 96, 0, 0, "orgnr=unresolved; navn=mangler; ekskludert=ikke_aktiv_crm", "2026-10-04")
                + "," + row(null, "Autorisasjon", 5, 0, 0, "ekskludert=ikke_aktiv_crm", "2026-10-05")
                + "," + row("123456789", "Formidling", 100, 1, 0, "inkludert=positiv_mengde_og_aktiv_crm", "2026-10-06")
                + "," + row("123456789", "Melding", 7, 0, 1, "dpv", "2026-10-06")
                + "," + row("123456789", "Formidling", 3, 1, 0, "inkludert=x", "2026-09-30")
                + "]}";
        var fil = DwhRespons.lesFlagget(json.getBytes(StandardCharsets.UTF_8),
                Periode.av(2026, 10));

        assertThat(fil).isNotNull();
        assertThat(fil.flagget()).isTrue();
        assertThat(fil.avviste()).isEmpty();
        assertThat(fil.brukte()).singleElement().satisfies(r -> {
            assertThat(r.organisasjonsnummer()).isEqualTo("123456789");
            assertThat(r.antall()).isEqualByComparingTo("100");
        });
        assertThat(fil.rapport().totalt()).isEqualTo(5);
        assertThat(fil.rapport().brukt()).isEqualTo(1);
        assertThat(fil.rapport().utelatt()).isEqualTo(2);
        assertThat(fil.rapport().dpv()).isEqualTo(1);
        assertThat(fil.rapport().utenforPerioden()).isEqualTo(1);
        assertThat(fil.rapport().balanserer()).isTrue();
    }

    @Test
    void responseWithoutFlagsIsLeftToTheOlderReader() {
        String json = "{\"value\":[{\"organisation_number\":\"123456789\",\"product_name\":\"Formidling\","
                + "\"quantity\":1,\"transaction_date\":\"2027-01-01\"}]}";
        assertThat(DwhRespons.lesFlagget(json.getBytes(StandardCharsets.UTF_8),
                Periode.av(2027, 1))).isNull();
    }

    @Test
    void flaggedResponseWithNextLinkIsStillRefused() {
        String json = "{\"value\":[" + row("123456789", "Formidling", 1, 1, 0, "x", "2026-10-06")
                + "],\"nextLink\":\"https://x/api/y?$after=z\"}";
        assertThatThrownBy(() -> DwhRespons.lesFlagget(json.getBytes(StandardCharsets.UTF_8),
                Periode.av(2026, 10))).isInstanceOf(Regelbrudd.class);
    }

    private static String row(String org, String produkt, int antall, int altinn, int dpv, String regel, String dato) {
        return "{\"organisation_number\":" + (org == null ? "null" : "\"" + org + "\"")
                + ",\"product_name\":\"" + produkt + "\",\"quantity\":" + antall + ",\"amount_nok\":null"
                + ",\"flag_inv_altinn\":" + altinn + ",\"flag_inv_dpv\":" + dpv + ",\"regel\":\"" + regel + "\""
                + ",\"source_table\":\"t\",\"resource_id\":\"urn:altinn:resource:r\""
                + ",\"transaction_date\":\"" + dato + "\",\"kundenummer\":null}";
    }

    static byte[] fixture(String navn) throws IOException {
        try (var inn = DwhResponsTest.class.getResourceAsStream("/dwh/" + navn)) {
            return inn.readAllBytes();
        }
    }
}
