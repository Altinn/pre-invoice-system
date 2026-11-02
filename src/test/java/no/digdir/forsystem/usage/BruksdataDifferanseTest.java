package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class BruksdataDifferanseTest {

    private static final LocalDate JAN = LocalDate.of(2027, 1, 1);

    @Test
    void showsOnlyAddedChangedAndRemovedKeysWithSignedDeltas() {
        List<Bruksdata> gammelt = List.of(
                volum(1L, "100000001", 1L, "100"),
                volum(2L, "100000002", 1L, "50"),
                kostnad(3L, "100000003", 2L, "1000.00"),
                volum(4L, "100000004", 1L, "7"));
        List<Bruksdata> nytt = List.of(
                volum(11L, "100000001", 1L, "120"),
                volum(12L, "100000002", 1L, "50.00"),
                kostnad(13L, "100000003", 2L, "750"),
                volum(14L, "100000005", 1L, "9"));

        var resultat = BruksdataDifferanse.sammenlign(gammelt, nytt);

        assertThat(resultat.uendredeRader()).isOne();
        assertThat(resultat.endringer()).extracting(e -> e.nokkel().organisasjonsnummer())
                .containsExactly("100000001", "100000003", "100000004", "100000005");
        assertThat(resultat.endringer()).extracting(BruksdataDifferanse.Endring::endringstype)
                .containsExactly(Endringstype.ENDRET, Endringstype.ENDRET,
                        Endringstype.FJERNET, Endringstype.TILAGT);
        assertThat(resultat.endringer()).extracting(BruksdataDifferanse.Endring::delta)
                .containsExactly(new BigDecimal("20"), new BigDecimal("-250.00"),
                        new BigDecimal("-7"), new BigDecimal("9"));
    }

    @Test
    void everyChangeReconcilesOldPlusDeltaToNew() {
        var resultat = BruksdataDifferanse.sammenlign(
                List.of(volum(1L, "100000001", 1L, "100"), kostnad(2L, "100000002", 2L, "25")),
                List.of(volum(3L, "100000001", 1L, "80"), kostnad(4L, "100000003", 2L, "12.50")));

        assertThat(resultat.endringer()).allSatisfy(endring -> {
            BigDecimal gammel = endring.gammelVerdi() == null ? BigDecimal.ZERO : endring.gammelVerdi();
            BigDecimal ny = endring.nyVerdi() == null ? BigDecimal.ZERO : endring.nyVerdi();
            assertThat(gammel.add(endring.delta())).isEqualByComparingTo(ny);
            assertThat(endring.delta()).isNotEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    private static Bruksdata volum(Long id, String orgnr, Long produkt, String antall) {
        return new Bruksdata(id, 1L, JAN, orgnr, produkt, Bruksdatatype.BRUKSVOLUM.name(),
                new BigDecimal(antall), null);
    }

    private static Bruksdata kostnad(Long id, String orgnr, Long produkt, String belop) {
        return new Bruksdata(id, 1L, JAN, orgnr, produkt, Bruksdatatype.AZURE_KOSTNAD.name(),
                null, new BigDecimal(belop));
    }
}
