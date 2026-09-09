package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Customer registration incl. the FinMod special cases (docs/01), plus audit on every write. */
class KundeServiceIT extends IntegrationTest {

    @Autowired KundeService service;
    @Autowired ProduktRepository produkter;
    @Autowired HendelsesloggRepository hendelser;

    @Test
    void registersDeviatingRecipientProductReferenceAndTwoKundenummer() {
        Kunde k = service.opprett("987654321", "Digitale Helgeland", "964338442", Avtalestatus.AKTIV);
        Long meldingId = produkter.findByKode("melding").orElseThrow().id();

        service.leggTilReferanse(k.id(), meldingId, "REF-MELDING-42", "BEST-1");
        service.leggTilRegel(k.id(), "KN-100", null, "100", null);
        service.leggTilRegel(k.id(), "KN-200", null, "200", null);

        assertThat(service.hent(k.id()).fakturamottakerOrgnr()).isEqualTo("964338442");
        assertThat(service.referanser(k.id())).hasSize(1)
                .allSatisfy(r -> assertThat(r.produktId()).isEqualTo(meldingId));
        assertThat(service.regler(k.id())).hasSize(2)
                .extracting(KundenummerRegel::servicekode).containsExactlyInAnyOrder("100", "200");
    }

    @Test
    void duplicateOrgnrIsRejected() {
        service.opprett("123456789", "Kunde A", null, Avtalestatus.AKTIV);
        assertThatThrownBy(() -> service.opprett("123456789", "Kunde B", null, Avtalestatus.AKTIV))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("finnes allerede");
    }

    @Test
    void invalidOrgnrIsRejected() {
        assertThatThrownBy(() -> service.opprett("12345", "Feil", null, Avtalestatus.AKTIV))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("9 siffer");
    }

    @Test
    void createWritesAuditRow() {
        Kunde k = service.opprett("555666777", "Revidert kommune", null, Avtalestatus.AKTIV);
        boolean funnet = hendelser.findTop200ByOrderByTidspunktDescIdDesc().stream()
                .anyMatch(h -> "OPPRETTET".equals(h.handling()) && "KUNDE".equals(h.entitet())
                        && k.id().equals(h.entitetId()));
        assertThat(funnet).isTrue();
    }
}
