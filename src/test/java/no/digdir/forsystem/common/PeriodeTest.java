package no.digdir.forsystem.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PeriodeTest {

    @Test
    void rejectsNonFirstOfMonth() {
        assertThatThrownBy(() -> new Periode(LocalDate.of(2027, 1, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAndFactoriesAgree() {
        assertThat(Periode.parse("2027-01")).isEqualTo(Periode.av(2027, 1));
        assertThat(Periode.av(2027, 1).førsteDag()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void forrigeGoesBackOneMonthAcrossYearBoundary() {
        assertThat(Periode.av(2027, 1).forrige()).isEqualTo(Periode.av(2026, 12));
    }

    @Test
    void labels() {
        assertThat(Periode.av(2027, 1).toString()).isEqualTo("2027-01");
        assertThat(Periode.av(2027, 1).norskLabel()).isEqualTo("januar 2027");
    }
}
