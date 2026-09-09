package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;

/** The run state machine: GENERERT → GODKJENT → (EKSPORTERT); FORKASTET; and the re-run rule. */
class KjoringStatemaskinIT extends BillingFixture {

    private Fakturakjoring renKjoring() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        return tjeneste.generer(JAN);
    }

    @Test
    void approveMovesGenerertToGodkjent() {
        var kj = renKjoring();
        tjeneste.godkjenn(kj.id());
        var etter = tjeneste.hent(kj.id());
        assertThat(etter.status()).isEqualTo("GODKJENT");
        assertThat(etter.godkjentAv()).isNotNull();
        assertThat(etter.godkjentAt()).isNotNull();
    }

    @Test
    void cannotApproveTwice() {
        var kj = renKjoring();
        tjeneste.godkjenn(kj.id());
        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("generert");
    }

    @Test
    void secondRunForSamePeriodBlockedUntilFirstForkastet() {
        var forste = renKjoring();
        // Partial unique index: only one non-FORKASTET run per period.
        assertThatThrownBy(() -> tjeneste.generer(JAN))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("aktiv kjøring");

        tjeneste.forkast(forste.id());
        var andre = tjeneste.generer(JAN);
        assertThat(andre.id()).isNotEqualTo(forste.id());
        assertThat(tjeneste.hent(forste.id()).status()).isEqualTo("FORKASTET");
        assertThat(tjeneste.hent(andre.id()).status()).isEqualTo("GENERERT");
    }

    @Test
    void exportedRunCannotBeForkastet() {
        var kj = renKjoring();
        tjeneste.godkjenn(kj.id());
        // Simulate export (Phase 4) by moving the run to EKSPORTERT directly.
        var g = tjeneste.hent(kj.id());
        kjoringer.save(new Fakturakjoring(g.id(), g.periode(), "EKSPORTERT", g.prisversjonId(),
                g.generertAv(), g.generertAt(), g.godkjentAv(), g.godkjentAt(), g.kommentar()));
        assertThatThrownBy(() -> tjeneste.forkast(kj.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("eksportert");
    }
}
