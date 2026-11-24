package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import no.digdir.forsystem.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;

/** Repository CRUD and the key database constraints from docs/03 (unique, nulls-not-distinct, checks). */
class RegistryRepositoryIT extends IntegrationTest {

    @Autowired ProduktRepository produkter;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired PrisRepository priser;

    @Test
    void seededProductsPresentAndKontringNull() {
        assertThat(produkter.findAllByOrderByKode()).hasSize(6);
        Produkt melding = produkter.findByKode("melding").orElseThrow();
        assertThat(melding.konto()).isNull();
        assertThat(melding.aktiv()).isTrue();
    }

    @Test
    void produktKodeIsUnique() {
        produkter.save(nyttProdukt("dummy-a"));
        assertThatThrownBy(() -> produkter.save(nyttProdukt("dummy-a")))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void prisIsUniquePerVersjonAndProdukt() {
        Prisversjon v = prisversjoner.save(new Prisversjon(
                null, "V-" + System.nanoTime(), LocalDate.of(2027, 1, 1), null, "UTKAST"));
        Long produktId = produkter.findByKode("melding").orElseThrow().id();
        priser.save(new Pris(null, v.id(), produktId, new BigDecimal("1.0000")));
        assertThatThrownBy(() -> priser.save(new Pris(null, v.id(), produktId, new BigDecimal("2.0000"))))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    private static Produkt nyttProdukt(String kode) {
        return new Produkt(null, kode, "Dummy", null, null, null, null, null, "transaksjon", true);
    }
}
