package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import no.digdir.forsystem.billing.Kontrollfunn;
import org.junit.jupiter.api.Test;

/** Findings are shown one row per code, blocking first (specs/005 FR-006/007). */
class FunngruppeTest {

    private static Kontrollfunn funn(String alvorlighet, String kode, String melding) {
        return new Kontrollfunn(null, 1L, null, alvorlighet, kode, melding);
    }

    @Test
    void grupperPerKodeOgAlvorlighetMedBlokkerendeForst() {
        var grupper = Funngruppe.av(List.of(
                funn("ADVARSEL", "AKTIV_KUNDE_UTEN_BRUK", "a1"),
                funn("ADVARSEL", "AKTIV_KUNDE_UTEN_BRUK", "a2"),
                funn("ADVARSEL", "AKTIV_KUNDE_UTEN_BRUK", "a3"),
                funn("BLOKKERENDE", "MANGLER_KUNDE", "m1"),
                funn("ADVARSEL", "B_KODE", "b1"),
                funn("BLOKKERENDE", "MANGLER_PRIS", "p1"),
                funn("BLOKKERENDE", "MANGLER_KUNDE", "m2")));

        assertThat(grupper).extracting(Funngruppe::kode)
                .containsExactly("MANGLER_KUNDE", "MANGLER_PRIS", "AKTIV_KUNDE_UTEN_BRUK", "B_KODE");
        assertThat(grupper).extracting(Funngruppe::antall).containsExactly(2, 1, 3, 1);
        assertThat(grupper.get(0).blokkerende()).isTrue();
        assertThat(grupper.get(2).blokkerende()).isFalse();
        assertThat(grupper.get(0).meldinger()).containsExactly("m1", "m2");
        assertThat(grupper.get(2).eksempel()).isEqualTo("a1");
    }

    @Test
    void likeStoreGrupperSorteresPaaKode() {
        var grupper = Funngruppe.av(List.of(funn("ADVARSEL", "Z", "z"), funn("ADVARSEL", "A", "a")));
        assertThat(grupper).extracting(Funngruppe::kode).containsExactly("A", "Z");
    }

    @Test
    void ingenFunnGirIngenGrupper() {
        assertThat(Funngruppe.av(List.of())).isEmpty();
    }
}
