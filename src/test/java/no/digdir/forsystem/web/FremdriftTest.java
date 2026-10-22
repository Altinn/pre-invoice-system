package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.web.Fremdrift.Tilstand;
import org.junit.jupiter.api.Test;

/** The run's progress strip: who did each step and when (specs/006 FR-006). */
class FremdriftTest {

    private static final OffsetDateTime T1 = OffsetDateTime.of(2027, 2, 7, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = T1.plusHours(2);
    private static final OffsetDateTime T3 = T1.plusDays(1);

    private static Fakturakjoring kjoring(String status, boolean godkjent) {
        return new Fakturakjoring(1L, LocalDate.of(2027, 1, 1), status, 1L, 1L, "ola", T1,
                godkjent ? "kari" : null, godkjent ? T2 : null, null);
    }

    private static Hendelseslogg hendelse(String handling, String bruker, OffsetDateTime naar) {
        return new Hendelseslogg(null, naar, bruker, handling, "FAKTURAKJORING", 1L, null);
    }

    private static List<Tilstand> tilstander(Fremdrift f) {
        return f.steg().stream().map(Fremdrift.Steg::tilstand).toList();
    }

    @Test
    void generertVenterPaaKontrollOgGodkjenning() {
        var f = Fremdrift.av(kjoring("GENERERT", false), List.of());
        assertThat(tilstander(f)).containsExactly(Tilstand.FERDIG, Tilstand.NAA, Tilstand.SENERE, Tilstand.SENERE);
        assertThat(f.steg().get(0).hvem()).isEqualTo("ola");
        assertThat(f.steg().get(0).naar()).isEqualTo(T1);
        assertThat(f.forkastet()).isNull();
    }

    @Test
    void godkjentVenterPaaEksport() {
        var f = Fremdrift.av(kjoring("GODKJENT", true), List.of());
        assertThat(tilstander(f)).containsExactly(Tilstand.FERDIG, Tilstand.FERDIG, Tilstand.NAA, Tilstand.SENERE);
        assertThat(f.steg().get(1).hvem()).isEqualTo("kari");
    }

    @Test
    void eksportertHenterHvemOgNaarFraHendelsesloggen() {
        var f = Fremdrift.av(kjoring("EKSPORTERT", true), List.of(
                hendelse("LASTET_NED", "per", T3.plusHours(1)),
                hendelse("EKSPORTERTE", "kari", T3),
                hendelse("GODKJENTE", "kari", T2)));
        assertThat(tilstander(f)).containsExactly(Tilstand.FERDIG, Tilstand.FERDIG, Tilstand.FERDIG, Tilstand.NAA);
        assertThat(f.steg().get(2).hvem()).isEqualTo("kari");
        assertThat(f.steg().get(2).naar()).isEqualTo(T3);
    }

    @Test
    void eksportUtenHendelseErFerdigUtenHvemOgNaar() {
        var f = Fremdrift.av(kjoring("EKSPORTERT", true), List.of());
        assertThat(f.steg().get(2).tilstand()).isEqualTo(Tilstand.FERDIG);
        assertThat(f.steg().get(2).hvem()).isNull();
        assertThat(f.steg().get(2).naar()).isNull();
    }

    @Test
    void forkastetForGodkjenning() {
        var f = Fremdrift.av(kjoring("FORKASTET", false), List.of(hendelse("FORKASTET", "ola", T2)));
        assertThat(tilstander(f)).containsExactly(Tilstand.FERDIG, Tilstand.SENERE, Tilstand.SENERE, Tilstand.SENERE);
        assertThat(f.forkastet().hvem()).isEqualTo("ola");
        assertThat(f.forkastet().naar()).isEqualTo(T2);
    }

    @Test
    void forkastetEtterGodkjenning() {
        var f = Fremdrift.av(kjoring("FORKASTET", true), List.of(hendelse("FORKASTET", "per", T3)));
        assertThat(tilstander(f)).containsExactly(Tilstand.FERDIG, Tilstand.FERDIG, Tilstand.SENERE, Tilstand.SENERE);
        assertThat(f.forkastet().hvem()).isEqualTo("per");
    }
}
