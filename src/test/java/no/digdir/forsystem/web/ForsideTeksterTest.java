package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import no.digdir.forsystem.common.Jsonb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The start page's small text rules (specs/015): first name, greeting, «… siden», activity sentences. */
class ForsideTeksterTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "adi.dahl@digdir.no, Adi",
            "KARI@digdir.no, Kari",
            "dev, NULL",
            "a1b2@digdir.no, NULL",
            "NULL, NULL",
    })
    void fornavnFraBrukernavn(String brukernavn, String forventet) {
        assertThat(Fornavn.fraBrukernavn(brukernavn)).isEqualTo(forventet);
    }

    @ParameterizedTest
    @CsvSource({
            "2027-02-10T06:00+01:00, God morgen",
            "2027-02-10T12:00+01:00, God dag",
            "2027-02-10T19:30+01:00, God kveld",
            "2027-02-10T02:00+01:00, God kveld",
    })
    void hilsenEtterTidPaaDognet(String tid, String forventet) {
        assertThat(Tidstekst.hilsen(ZonedDateTime.parse(tid))).isEqualTo(forventet);
    }

    @Test
    void dagSkrivesPaaNorsk() {
        assertThat(Tidstekst.dag(ZonedDateTime.parse("2026-11-11T09:00+01:00"))).isEqualTo("onsdag 11. november 2026");
    }

    @ParameterizedTest
    @CsvSource({
            "2027-02-10T11:59:30+01:00, nå nettopp",
            "2027-02-10T11:48+01:00, for 12 min siden",
            "2027-02-10T08:00+01:00, for 4 t siden",
            "2027-02-09T23:00+01:00, i går",
            "2027-02-06T12:00+01:00, for 4 dager siden",
            "2026-12-01T12:00+01:00, 01.12.2026",
    })
    void sidenLeserNaturlig(String da, String forventet) {
        ZonedDateTime naa = ZonedDateTime.parse("2027-02-10T12:00+01:00");
        assertThat(Tidstekst.siden(OffsetDateTime.parse(da), naa)).isEqualTo(forventet);
    }

    @Test
    void aktivitetMedPeriode() {
        assertThat(Aktivitetstekst.av("GODKJENTE", "FAKTURAKJORING", Jsonb.of("{\"periode\":\"2027-01-01\"}")))
                .isEqualTo("Godkjente kjøring for januar 2027");
    }

    @Test
    void ukjentHandlingVisesRaatt() {
        assertThat(Aktivitetstekst.av("NOE_NYTT", "ANNET_OBJEKT", null)).isEqualTo("noe nytt annet objekt");
        assertThat(Aktivitetstekst.bruker("system")).isEqualTo("System");
    }
}
