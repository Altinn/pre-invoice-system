package no.digdir.forsystem.web;

import java.math.BigDecimal;

import no.digdir.forsystem.billing.Fakturakjoring;

/** One row of the run list: enough to see a run's size and problems without opening it (specs/006 FR-012). */
public record Kjoringsrad(Fakturakjoring kjoring, int antallFakturaer, BigDecimal sumBelop,
                          long antallBlokkerende, long antallAdvarsler) {
}
