package no.digdir.forsystem.usage;

/**
 * A raw, unvalidated usage row as read from a source (all fields as text). The shared validation
 * pipeline in {@link UsageImportService} turns these into valid {@link Bruksdata} or {@link AvvistRad}.
 * {@code linjenr} is the 1-based line number in the source file, for reporting rejects.
 */
public record RaaBruksrad(
        int linjenr,
        String periode,
        String organisasjonsnummer,
        String produktkode,
        String type,
        String antall,
        String belop) {
}
