package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.digdir.forsystem.billing.Faktura;
import org.junit.jupiter.api.Test;

/** Invoice search, sort and paging on the Fakturaer tab (specs/006 FR-008/009). */
class FakturautvalgTest {

    private final List<Faktura> fakturaer = new ArrayList<>();
    private final Map<Long, String> navn = new LinkedHashMap<>();

    private void faktura(long id, int ordre, String kundenavn, String kundenummer, String orgnr, String belop) {
        fakturaer.add(new Faktura(id, 1L, id, UUID.randomUUID(), ordre, kundenummer, kundenavn, null, orgnr,
                new BigDecimal(belop)));
        navn.put(id, kundenavn);
    }

    private List<Integer> ordre(Fakturautvalg u) {
        return u.rader().stream().map(r -> r.faktura().ordreNr()).toList();
    }

    @Test
    void sokerINavnOrgnrKundenummerOgOrdreUtenAaSkilleStoreOgSmaa() {
        faktura(1, 1, "Oslo kommune", "KN-1001", "958935420", "100.00");
        faktura(2, 2, "Øvre Eiker kommune", "KN-2002", "938709866", "50.00");
        faktura(3, 13, "Digitale Helgeland", "KN-1003", "964338442", "25.00");

        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, "OSLO", null, null))).containsExactly(1);
        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, "9387", null, null))).containsExactly(2);
        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, "kn-1003", null, null))).containsExactly(13);
        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, " 13 ", null, null))).containsExactly(13);

        var treff = Fakturautvalg.av(fakturaer, navn, "kommune", null, null);
        assertThat(treff.antallTreff()).isEqualTo(2);
        assertThat(treff.sumTreff()).isEqualByComparingTo("150.00");
        assertThat(treff.antallTotalt()).isEqualTo(3);
        assertThat(treff.soker()).isTrue();
    }

    @Test
    void sortererPaaOrdreKundeOgBelop() {
        faktura(1, 2, "Oslo kommune", "A", "1", "10.00");
        faktura(2, 1, "Øvre Eiker kommune", "B", "2", "30.00");
        faktura(3, 3, "Asker kommune", "C", "3", "20.00");

        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, null, null, null))).containsExactly(1, 2, 3);
        // Norwegian collation: Ø after O.
        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, null, "kunde", null))).containsExactly(3, 2, 1);
        assertThat(ordre(Fakturautvalg.av(fakturaer, navn, null, "belop", null))).containsExactly(1, 3, 2);
        assertThat(Fakturautvalg.av(fakturaer, navn, null, "tull", null).sorter()).isEqualTo("ordre");
    }

    @Test
    void viser50PerSideOgHolderSidenInnenforGrensene() {
        for (int i = 1; i <= 120; i++) {
            faktura(i, i, "Kunde " + i, "KN" + i, "9" + i, "1.00");
        }
        var side1 = Fakturautvalg.av(fakturaer, navn, null, null, null);
        assertThat(side1.rader()).hasSize(50);
        assertThat(side1.side()).isEqualTo(1);
        assertThat(side1.antallSider()).isEqualTo(3);

        var side3 = Fakturautvalg.av(fakturaer, navn, null, null, 3);
        assertThat(ordre(side3)).first().isEqualTo(101);
        assertThat(side3.rader()).hasSize(20);

        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, 99).side()).isEqualTo(3);
        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, -2).side()).isEqualTo(1);
    }

    @Test
    void sidelenkerViserEllipseNaarDetErMangeSider() {
        for (int i = 1; i <= 50 * 12; i++) {
            faktura(i, i, "K" + i, "KN" + i, "9" + i, "1.00");
        }
        // Same window as Designsystemet's <ds-pagination> with 7 slots; 0 is an ellipsis.
        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, 1).sider()).containsExactly(1, 2, 3, 4, 5, 0, 12);
        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, 6).sider()).containsExactly(1, 0, 5, 6, 7, 0, 12);
        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, 12).sider()).containsExactly(1, 0, 8, 9, 10, 11, 12);
    }

    @Test
    void faaSiderVisesUtenEllipse() {
        for (int i = 1; i <= 120; i++) {
            faktura(i, i, "K" + i, "KN" + i, "9" + i, "1.00");
        }
        assertThat(Fakturautvalg.av(fakturaer, navn, null, null, 2).sider()).containsExactly(1, 2, 3);
    }

    @Test
    void ingenTreffGirEnTomSide() {
        faktura(1, 1, "Oslo kommune", "KN-1001", "958935420", "100.00");
        var u = Fakturautvalg.av(fakturaer, navn, "finnes ikke", null, null);
        assertThat(u.rader()).isEmpty();
        assertThat(u.antallTreff()).isZero();
        assertThat(u.sumTreff()).isEqualByComparingTo("0");
        assertThat(u.antallSider()).isEqualTo(1);
    }
}
