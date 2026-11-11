package no.digdir.forsystem.web;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Human time texts for the start page (specs/015), in Norwegian time. These read the clock only to say how
 * long ago something happened or what day it is — never to pick a billing period.
 */
final class Tidstekst {

    static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final Locale NORSK = Locale.forLanguageTag("nb-NO");
    private static final DateTimeFormatter DATO = DateTimeFormatter.ofPattern("dd.MM.yyyy", NORSK);
    private static final DateTimeFormatter DATO_TID = DateTimeFormatter.ofPattern("dd.MM.yyyy 'kl.' HH:mm", NORSK);
    private static final DateTimeFormatter DAG = DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", NORSK);
    private static final DateTimeFormatter PLAN = DateTimeFormatter.ofPattern("d. MMMM 'kl.' HH:mm", NORSK);

    private Tidstekst() {
    }

    /** «God morgen» 05–10, «God dag» 10–18, otherwise «God kveld». */
    static String hilsen(ZonedDateTime naa) {
        int time = naa.withZoneSameInstant(OSLO).getHour();
        if (time >= 5 && time < 10) {
            return "God morgen";
        }
        if (time >= 10 && time < 18) {
            return "God dag";
        }
        return "God kveld";
    }

    /** «onsdag 11. november 2026». */
    static String dag(ZonedDateTime naa) {
        return DAG.format(naa.withZoneSameInstant(OSLO));
    }

    /** «7. mars kl. 06:00». */
    static String planlagt(ZonedDateTime tidspunkt) {
        return PLAN.format(tidspunkt.withZoneSameInstant(OSLO));
    }

    /** «07.02.2027 kl. 06:02». */
    static String datoTid(OffsetDateTime t) {
        return DATO_TID.format(t.atZoneSameInstant(OSLO));
    }

    /** «nå nettopp», «for 12 min siden», «for 3 t siden», «i går», «for 4 dager siden», else the date. */
    static String siden(OffsetDateTime t, ZonedDateTime naa) {
        Duration d = Duration.between(t.toInstant(), naa.toInstant());
        if (d.isNegative() || d.toMinutes() < 1) {
            return "nå nettopp";
        }
        if (d.toMinutes() < 60) {
            return "for " + d.toMinutes() + " min siden";
        }
        LocalDate dagen = t.atZoneSameInstant(OSLO).toLocalDate();
        LocalDate idag = naa.withZoneSameInstant(OSLO).toLocalDate();
        if (dagen.equals(idag)) {
            return "for " + d.toHours() + " t siden";
        }
        if (dagen.equals(idag.minusDays(1))) {
            return "i går";
        }
        long dager = java.time.temporal.ChronoUnit.DAYS.between(dagen, idag);
        if (dager < 30) {
            return "for " + dager + " dager siden";
        }
        return DATO.format(dagen);
    }
}
