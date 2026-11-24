package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KundedataTest {

    @Test
    void enVerdiPerFeltGirIngenKonflikt() {
        List<Kundedata> k = new Kundedata.Samler()
                .legg("111111111", "finanstilsynet", "5663", "REF", null)
                .legg("111111111", "finanstilsynet", "5663", "REF", null)
                .resultat();
        assertThat(k).containsExactly(new Kundedata("111111111", "finanstilsynet", "5663", "REF", null, List.of()));
    }

    @Test
    void tomVerdiErIkkeKonflikt() {
        Kundedata k = new Kundedata.Samler()
                .legg("111111111", null, "5663", null, " ")
                .legg("111111111", "navn", null, "REF", null)
                .resultat().getFirst();
        assertThat(k.kundenummer()).isEqualTo("5663");
        assertThat(k.fakturareferanse()).isEqualTo("REF");
        assertThat(k.bestillingsnummer()).isNull();
        assertThat(k.navn()).isEqualTo("navn");
        assertThat(k.harMotstridende()).isFalse();
    }

    @Test
    void ulikeKundenummerBlirMotstridendeIRekkefolge() {
        Kundedata k = new Kundedata.Samler()
                .legg("111111111", "a", "5663", "REF", null)
                .legg("111111111", "a", "5664", "REF", null)
                .resultat().getFirst();
        assertThat(k.kundenummer()).isEqualTo("5663");
        assertThat(k.motstridende()).containsExactly("kundenummer: 5663 | 5664");
    }

    @Test
    void ulikeNavnErIkkeKonflikt() {
        Kundedata k = new Kundedata.Samler()
                .legg("111111111", "første", "1", null, null)
                .legg("111111111", "andre", "1", null, null)
                .resultat().getFirst();
        assertThat(k.navn()).isEqualTo("første");
        assertThat(k.harMotstridende()).isFalse();
    }

    @Test
    void treFeltIKonfliktGirTreLinjer() {
        Kundedata k = new Kundedata.Samler()
                .legg("111111111", null, "1", "A", "X")
                .legg("111111111", null, "2", "B", "Y")
                .resultat().getFirst();
        assertThat(k.motstridende()).containsExactly(
                "kundenummer: 1 | 2", "fakturareferanse: A | B", "bestillingsnummer: X | Y");
    }

    @Test
    void organisasjonerIForsteRekkefolge() {
        List<Kundedata> k = new Kundedata.Samler()
                .legg("222222222", null, "2", null, null)
                .legg("111111111", null, "1", null, null)
                .legg("222222222", null, "2", null, null)
                .resultat();
        assertThat(k).extracting(Kundedata::organisasjonsnummer).containsExactly("222222222", "111111111");
    }
}
