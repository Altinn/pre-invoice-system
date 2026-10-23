package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;

/** Parsing the datavarehus's own CSV export (fake orgnrs, same shape as the real file). */
class DwhCsvTest {

    private byte[] eksport() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/dwh/eksport-2026-09.csv")) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsRowsWithQuotesCommasAndNull() throws IOException {
        var rader = DwhCsv.les(eksport());

        assertThat(rader).hasSize(4);
        var forste = rader.get(0);
        assertThat(forste.organisasjonsnummer()).isEqualTo("100000001");
        assertThat(forste.produktnavn()).isEqualTo("Autorisasjon");
        assertThat(forste.antall()).isEqualByComparingTo("1200");
        assertThat(forste.belop()).isNull();                       // NULL
        assertThat(forste.dato()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(forste.kundenummer()).isEqualTo("9001");
        assertThat(rader.get(2).fakturareferanse()).isEqualTo("Navn, med komma");
        assertThat(rader.get(3).fakturareferanse()).isNull();
    }

    @Test
    void oneCustomerPerOrganisation() throws IOException {
        var kunder = DwhCsv.kunder(eksport());

        assertThat(kunder).extracting(DwhCsv.KundeIFil::organisasjonsnummer)
                .containsExactly("100000001", "100000002", "100000003");
        assertThat(kunder.get(0).navn()).isEqualTo("testetat en");
        assertThat(kunder.get(0).bestillingsnummer()).isNull();
    }

    @Test
    void forsystemsOwnCsvFormatIsRefusedWithAHint() {
        byte[] egen = "periode,organisasjonsnummer,produktkode,type,antall,belop\n2026-09-01,100000001,melding,BRUKSVOLUM,1,\n"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DwhCsv.les(egen))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("ikke ut som en eksport fra datavarehuset");
    }

    @Test
    void quotedNullIsTextAndBareNullIsMissing() {
        byte[] fil = ("organisation_number,product_name,quantity,amount_nok,maaned,fakturareferanse\n"
                + "100000001,Formidling,5,NULL,2026-09-01,\"NULL\"\n").getBytes(StandardCharsets.UTF_8);
        var rad = DwhCsv.les(fil).get(0);
        assertThat(rad.belop()).isNull();
        assertThat(rad.fakturareferanse()).isEqualTo("NULL");
        assertThat(rad.antall()).isEqualTo(new BigDecimal("5"));
    }

    @Test
    void readsTheRenamedLayoutWithTransactionMonth() throws IOException {
        byte[] fil;
        try (InputStream in = getClass().getResourceAsStream("/dwh/eksport-2026-08-transaction-month.csv")) {
            fil = in.readAllBytes();
        }
        var rader = DwhCsv.les(fil);

        assertThat(rader).hasSize(3);
        assertThat(rader).allSatisfy(r -> assertThat(r.dato()).isEqualTo(LocalDate.of(2026, 8, 1)));
        assertThat(rader.get(0).kundenummer()).isEqualTo("9001");            // columns matched by name
        assertThat(rader.get(1).antall()).isNull();
        assertThat(rader.get(1).belop()).isEqualByComparingTo("1234.567890123456789012");
    }

    @Test
    void missingDateColumnNamesTheAcceptedOnesAndListsWhatWasFound() {
        byte[] fil = "organisation_number,product_name,quantity,amount_nok,periode_start\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DwhCsv.les(fil))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("transaction_date, transaction_month, maaned")
                .hasMessageContaining("Kolonner i fila: organisation_number, product_name, quantity, amount_nok, periode_start.");
    }
}
