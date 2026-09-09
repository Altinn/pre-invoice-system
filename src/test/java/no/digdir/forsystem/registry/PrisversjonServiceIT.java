package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Price-version lifecycle and validation rules (docs/04 Phase 1). */
class PrisversjonServiceIT extends IntegrationTest {

    @Autowired PrisversjonService service;
    @Autowired ProduktRepository produkter;
    @Autowired HendelsesloggRepository hendelser;

    @Test
    void activateSucceedsWhenAllActiveProductsPricedAndSetsStatusAndAudit() {
        Prisversjon v = service.opprettUtkast("2027", LocalDate.of(2027, 1, 1), null);
        prisAlleAktive(v.id(), "1.2500");

        service.aktiver(v.id());

        assertThat(service.hent(v.id()).statusEnum()).isEqualTo(PrisversjonStatus.AKTIV);
        boolean auditFinnes = hendelser.findTop200ByOrderByTidspunktDescIdDesc().stream()
                .anyMatch(h -> "AKTIVERTE".equals(h.handling()) && "PRISVERSJON".equals(h.entitet())
                        && v.id().equals(h.entitetId()));
        assertThat(auditFinnes).isTrue();
    }

    @Test
    void activateBlockedWhenAnActiveProductLacksPrice() {
        Prisversjon v = service.opprettUtkast("mangel", LocalDate.of(2030, 1, 1), null);
        // Price all but one active product.
        var aktive = produkter.findByAktivTrue();
        for (int i = 1; i < aktive.size(); i++) {
            service.settPris(v.id(), aktive.get(i).id(), new BigDecimal("1.0000"));
        }
        assertThatThrownBy(() -> service.aktiver(v.id()))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("mangler pris");
    }

    @Test
    void settPrisBlockedAfterActivation() {
        Prisversjon v = service.opprettUtkast("laast", LocalDate.of(2031, 1, 1), null);
        prisAlleAktive(v.id(), "2.0000");
        service.aktiver(v.id());
        Long produktId = produkter.findByAktivTrue().get(0).id();
        assertThatThrownBy(() -> service.settPris(v.id(), produktId, new BigDecimal("9.0000")))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("utkast");
    }

    @Test
    void activateBlockedWhenDateRangeOverlapsAnotherActiveVersion() {
        Prisversjon v1 = service.opprettUtkast("aapent-2027", LocalDate.of(2027, 1, 1), null);
        prisAlleAktive(v1.id(), "1.0000");
        service.aktiver(v1.id());

        Prisversjon v2 = service.opprettUtkast("overlapp", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 12, 31));
        prisAlleAktive(v2.id(), "1.0000");
        assertThatThrownBy(() -> service.aktiver(v2.id()))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("overlapper");
    }

    @Test
    void archiveMovesActiveToArkivert() {
        Prisversjon v = service.opprettUtkast("arkiv", LocalDate.of(2032, 1, 1), null);
        prisAlleAktive(v.id(), "1.0000");
        service.aktiver(v.id());
        service.arkiver(v.id());
        assertThat(service.hent(v.id()).statusEnum()).isEqualTo(PrisversjonStatus.ARKIVERT);
    }

    private void prisAlleAktive(Long versjonId, String pris) {
        produkter.findByAktivTrue().forEach(p -> service.settPris(versjonId, p.id(), new BigDecimal(pris)));
    }
}
