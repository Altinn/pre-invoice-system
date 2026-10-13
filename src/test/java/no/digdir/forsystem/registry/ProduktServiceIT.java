package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;

import no.digdir.forsystem.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The product list puts inactive products last (specs/003 D3, BF1 F03). */
class ProduktServiceIT extends IntegrationTest {

    @Autowired ProduktService service;
    @Autowired ProduktRepository produkter;

    @Test
    void activeProductsFirstThenInactiveEachByCode() {
        Produkt studio = produkter.findByKode("studio").orElseThrow();
        produkter.save(new Produkt(studio.id(), studio.kode(), studio.navn(), studio.artikkelId(), studio.konto(),
                studio.dim1(), studio.dim2(), studio.dim4(), studio.enhet(), false));

        var koder = service.oversikt().stream().map(Produkt::kode).toList();

        assertThat(koder.get(koder.size() - 1)).isEqualTo("studio");
        assertThat(koder.subList(0, koder.size() - 1)).isSorted().doesNotContain("studio");
    }
}
