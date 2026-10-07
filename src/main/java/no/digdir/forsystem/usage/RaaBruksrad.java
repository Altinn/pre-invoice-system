package no.digdir.forsystem.usage;

/**
 * A raw, unvalidated usage row as read from a source (all fields as text). The shared validation
 * pipeline in {@link UsageImportService} turns these into valid {@link Bruksdata} or {@link AvvistRad}.
 * {@code referanse} identifies the row in the source for reporting rejects: the 1-based line number
 * for CSV, {@code orgnr/produkt/type} for an aggregated datavarehus row.
 */
public record RaaBruksrad(
        String referanse,
        String periode,
        String organisasjonsnummer,
        String produktkode,
        String type,
        String antall,
        String belop) {
}
