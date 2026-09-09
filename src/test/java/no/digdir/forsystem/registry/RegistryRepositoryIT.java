package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    @Autowired KundeRepository kunder;
    @Autowired KundeReferanseRepository referanser;
    @Autowired KundenummerRegelRepository regler;

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

    @Test
    void kundeOrgnrUniqueAndFormatChecked() {
        kunder.save(nyKunde("999888777"));
        assertThatThrownBy(() -> kunder.save(nyKunde("999888777")))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void kundeOrgnrFormatConstraintRejectsNonNineDigits() {
        assertThatThrownBy(() -> kunder.save(nyKunde("12345")))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void kundeReferanseNullsNotDistinctUnique() {
        Long kundeId = kunder.save(nyKunde("111222333")).id();
        referanser.save(new KundeReferanse(null, kundeId, null, "ref-1", null));
        // A second customer-default row (produktId null) must collide under nulls-not-distinct.
        assertThatThrownBy(() -> referanser.save(new KundeReferanse(null, kundeId, null, "ref-2", null)))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void kundenummerRegelNullsNotDistinctUnique() {
        Long kundeId = kunder.save(nyKunde("222333444")).id();
        regler.save(new KundenummerRegel(null, kundeId, "KN-1", null, null, null));
        assertThatThrownBy(() -> regler.save(new KundenummerRegel(null, kundeId, "KN-2", null, null, null)))
                .isInstanceOf(DbActionExecutionException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    private static Produkt nyttProdukt(String kode) {
        return new Produkt(null, kode, "Dummy", null, null, null, null, null, "transaksjon", true);
    }

    private static Kunde nyKunde(String orgnr) {
        return new Kunde(null, orgnr, "Testkunde", null, "AKTIV", "MANUELL", "test", OffsetDateTime.now());
    }
}
