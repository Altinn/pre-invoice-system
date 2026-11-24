package no.digdir.forsystem.usage;

/**
 * A raw, unvalidated usage row as read from a source (all fields as text). The shared validation
 * pipeline in {@link UsageImportService} turns these into valid {@link Bruksdata} or {@link AvvistRad}.
 * {@code referanse} identifies the row in the source for reporting rejects: the 1-based line number
 * for CSV, {@code orgnr/produkt/type} for an aggregated datavarehus row. The customer fields are the optional
 * columns of forsystem's own CSV format (specs/018 D3); null when absent and for datavarehus rows, whose
 * customer values are collected before aggregation.
 */
public record RaaBruksrad(
        String referanse,
        String periode,
        String organisasjonsnummer,
        String produktkode,
        String type,
        String antall,
        String belop,
        String kundenavn,
        String kundenummer,
        String fakturareferanse,
        String bestillingsnummer) {

    /** A row without customer columns. */
    public RaaBruksrad(String referanse, String periode, String organisasjonsnummer, String produktkode, String type,
                       String antall, String belop) {
        this(referanse, periode, organisasjonsnummer, produktkode, type, antall, belop, null, null, null, null);
    }
}
