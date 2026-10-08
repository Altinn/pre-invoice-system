package no.digdir.forsystem.usage;

/**
 * A rejected usage row and the reason, shown in the staging report. {@code referanse} points back into
 * the source: a CSV line number, or {@code orgnr/produktnavn} for a datavarehus row.
 */
public record AvvistRad(String referanse, String aarsak) {
}
