package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** One number format on every page: 1 234 567,89 (specs/010 FR-001/FR-002). */
class TallformatTest {

    private final Tallformat tall = new Tallformat();

    @Test
    void amountsHaveSpaceThousandsCommaAndTwoDecimals() {
        assertThat(tall.belop(new BigDecimal("1234567.891"))).isEqualTo("1 234 567,89");
        assertThat(tall.belop(new BigDecimal("0.005"))).isEqualTo("0,01");
        assertThat(tall.belop(BigDecimal.ZERO)).isEqualTo("0,00");
    }

    @Test
    void negativeAmountsKeepTheirSign() {
        assertThat(tall.belop(new BigDecimal("-1234.5"))).isEqualTo("-1 234,50");
    }

    @Test
    void countsHaveNoDecimals() {
        assertThat(tall.antall(1234567)).isEqualTo("1 234 567");
        assertThat(tall.antall(1234567L)).isEqualTo("1 234 567");
        assertThat(tall.antall(0)).isEqualTo("0");
    }

    @Test
    void quantitiesHaveTwoDecimals() {
        assertThat(tall.mengde(new BigDecimal("1234"))).isEqualTo("1 234,00");
    }

    @Test
    void pricesKeepEveryStoredDecimalButAtLeastTwo() {
        assertThat(tall.pris(new BigDecimal("0.0375"))).isEqualTo("0,0375");
        assertThat(tall.pris(new BigDecimal("1250"))).isEqualTo("1 250,00");
        assertThat(tall.pris(new BigDecimal("1.2500"))).isEqualTo("1,25");
    }

    @Test
    void missingValuesShowADash() {
        assertThat(tall.belop(null)).isEqualTo("—");
        assertThat(tall.antall(null)).isEqualTo("—");
        assertThat(tall.mengde(null)).isEqualTo("—");
        assertThat(tall.pris(null)).isEqualTo("—");
    }
}
