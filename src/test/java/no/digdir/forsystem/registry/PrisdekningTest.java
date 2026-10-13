package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import no.digdir.forsystem.common.Periode;
import org.junit.jupiter.api.Test;

/** The covered-period rule shown to users must be the rule generation applies (specs/003 D2). */
class PrisdekningTest {

    @Test
    void openEndedFromTheFirst() {
        var d = Prisdekning.fra(LocalDate.of(2027, 1, 1), null);
        assertThat(d.forste()).isEqualTo(Periode.av(2027, 1));
        assertThat(d.siste()).isNull();
        assertThat(d.tekst()).isEqualTo("Brukes for kjøringer fra januar 2027 og fremover.");
    }

    @Test
    void midMonthStartCoversFromTheNextMonth() {
        var d = Prisdekning.fra(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 12, 31));
        assertThat(d.forste()).isEqualTo(Periode.av(2027, 2));
        assertThat(d.siste()).isEqualTo(Periode.av(2027, 12));
        assertThat(d.tekst()).isEqualTo("Brukes for kjøringer fra februar 2027 til og med desember 2027.");
    }

    @Test
    void rangeInsideOneMonthAfterItsFirstDayCoversNothing() {
        var d = Prisdekning.fra(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 31));
        assertThat(d.ingen()).isTrue();
        assertThat(d.tekst()).startsWith("Dekker ingen perioder");
    }

    @Test
    void singleDayOnTheFirstCoversThatMonth() {
        var d = Prisdekning.fra(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1));
        assertThat(d.forste()).isEqualTo(Periode.av(2027, 3));
        assertThat(d.siste()).isEqualTo(Periode.av(2027, 3));
        assertThat(d.tekst()).isEqualTo("Brukes for kjøringer for mars 2027.");
    }
}
