package no.digdir.forsystem.usage;

/** A rejected usage row with its file line number and the reason, shown in the staging report. */
public record AvvistRad(int linjenr, String aarsak) {
}
