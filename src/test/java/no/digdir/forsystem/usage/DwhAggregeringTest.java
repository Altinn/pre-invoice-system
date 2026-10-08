package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.Periode;
import org.junit.jupiter.api.Test;

/** Daily datavarehus rows → monthly raw rows via the product-name mapping (FR-002, FR-004). */
class DwhAggregeringTest {

    private static final Periode JAN = Periode.av(2027, 1);
    private static final Map<String, DwhAggregering.Maal> KOBLING = Map.of(
            "Formidling", new DwhAggregering.Maal("formidling", Bruksdatatype.BRUKSVOLUM),
            "Varsling e-post", new DwhAggregering.Maal("varsling", Bruksdatatype.BRUKSVOLUM),
            "Varsling SMS", new DwhAggregering.Maal("varsling", Bruksdatatype.BRUKSVOLUM),
            "SMS-kost", new DwhAggregering.Maal("varsling", Bruksdatatype.SMS_KOSTNAD));

    @Test
    void sumsDailyRowsPerOrgProductAndType() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                rad("123456789", "Formidling", 1, "100"),
                rad("123456789", "Formidling", 31, "50"),
                rad("987654321", "Formidling", 2, "1")), KOBLING);

        assertThat(res.avviste()).isEmpty();
        assertThat(res.rader()).hasSize(2);
        assertThat(res.rader()).anySatisfy(r -> {
            assertThat(r.organisasjonsnummer()).isEqualTo("123456789");
            assertThat(r.produktkode()).isEqualTo("formidling");
            assertThat(r.type()).isEqualTo("BRUKSVOLUM");
            assertThat(r.periode()).isEqualTo("2027-01-01");
            assertThat(r.antall()).isEqualTo("150");
            assertThat(r.belop()).isNull();
        });
    }

    @Test
    void severalNamesMappedToSameProductAreSummed() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                rad("123456789", "Varsling e-post", 1, "7"),
                rad("123456789", "Varsling SMS", 1, "3")), KOBLING);
        assertThat(res.rader()).singleElement().satisfies(r -> assertThat(r.antall()).isEqualTo("10"));
    }

    @Test
    void unmappedNameIsRejectedOncePerOrgAndName() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                rad("123456789", "Ukjent", 1, "1"),
                rad("123456789", "Ukjent", 2, "1"),
                rad("987654321", "Ukjent", 2, "1")), KOBLING);
        assertThat(res.rader()).isEmpty();
        assertThat(res.avviste()).hasSize(2)
                .allSatisfy(a -> assertThat(a.aarsak()).contains("ukjent produktnavn i datavarehus: Ukjent"));
        assertThat(res.avviste().getFirst().aarsak()).contains("2 dagsrader");
    }

    @Test
    void rowOutsidePeriodIsRejected() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                new DwhRad("123456789", "Formidling", BigDecimal.ONE, null, LocalDate.of(2027, 2, 1), null, null)),
                KOBLING);
        assertThat(res.rader()).isEmpty();
        assertThat(res.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("feil periode"));
    }

    @Test
    void fractionalOrNegativeQuantityIsRejected() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                rad("123456789", "Formidling", 1, "1.5"),
                rad("123456789", "Formidling", 2, "-1")), KOBLING);
        assertThat(res.rader()).isEmpty();
        assertThat(res.avviste()).hasSize(2).allSatisfy(a -> assertThat(a.aarsak()).contains("heltall"));
    }

    @Test
    void amountOnVolumeMappingIsRejected() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                new DwhRad("123456789", "Formidling", BigDecimal.ONE, new BigDecimal("10"), LocalDate.of(2027, 1, 1), null, null)),
                KOBLING);
        assertThat(res.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("amount_nok"));
    }

    @Test
    void costMappingSumsAmount() {
        var res = DwhAggregering.aggreger(JAN, List.of(
                new DwhRad("123456789", "SMS-kost", null, new BigDecimal("10.50"), LocalDate.of(2027, 1, 1), null, null),
                new DwhRad("123456789", "SMS-kost", null, new BigDecimal("2.25"), LocalDate.of(2027, 1, 2), null, null)),
                KOBLING);
        assertThat(res.rader()).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo("SMS_KOSTNAD");
            assertThat(r.belop()).isEqualTo("12.75");
            assertThat(r.antall()).isNull();
        });
    }

    private static DwhRad rad(String orgnr, String navn, int dag, String antall) {
        return new DwhRad(orgnr, navn, new BigDecimal(antall), null, LocalDate.of(2027, 1, dag), null, null);
    }
}
