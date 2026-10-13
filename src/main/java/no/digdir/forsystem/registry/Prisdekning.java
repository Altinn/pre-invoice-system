package no.digdir.forsystem.registry;

import java.time.LocalDate;

import no.digdir.forsystem.common.Periode;

/**
 * Which billing periods a price version is used for (specs/003 D2, BF1 F05). Mirrors the rule
 * generation applies: a run for a period uses the active version whose dates — both inclusive, an empty
 * "til" meaning open-ended — contain the period's <em>first day</em>. So a version starting mid-month
 * only covers the following months, and one that starts and ends inside a single month covers none.
 *
 * @param forste the first covered period, or {@code null} if none is covered
 * @param siste  the last covered period, or {@code null} when open-ended (or none is covered)
 */
public record Prisdekning(Periode forste, Periode siste) {

    public static Prisdekning fra(LocalDate gyldigFra, LocalDate gyldigTil) {
        LocalDate forsteDag = gyldigFra.getDayOfMonth() == 1 ? gyldigFra : gyldigFra.withDayOfMonth(1).plusMonths(1);
        if (gyldigTil == null) {
            return new Prisdekning(new Periode(forsteDag), null);
        }
        LocalDate sisteDag = gyldigTil.withDayOfMonth(1);
        if (forsteDag.isAfter(sisteDag)) {
            return new Prisdekning(null, null);
        }
        return new Prisdekning(new Periode(forsteDag), new Periode(sisteDag));
    }

    public boolean ingen() {
        return forste == null;
    }

    /** The coverage in plain Norwegian, as shown on the price version's page. */
    public String tekst() {
        if (ingen()) {
            return "Dekker ingen perioder: en periode dekkes bare hvis dens første dag er innenfor datoene.";
        }
        if (siste == null) {
            return "Brukes for kjøringer fra " + forste.norskLabel() + " og fremover.";
        }
        if (siste.equals(forste)) {
            return "Brukes for kjøringer for " + forste.norskLabel() + ".";
        }
        return "Brukes for kjøringer fra " + forste.norskLabel() + " til og med " + siste.norskLabel() + ".";
    }
}
