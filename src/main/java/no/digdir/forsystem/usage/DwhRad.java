package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the datavarehus view {@code mv_altinn_usage_monthly}: one day's usage for one
 * organisation and DWH product name. Transient — it is aggregated to monthly {@link RaaBruksrad}s and
 * only survives in the archived raw payload. {@code kundenummer}/{@code fakturareferanse} are the
 * DWH's copy of customer data, used for cross-checking only (the registry is the master).
 *
 * <p>The flagged export (specs/017) adds {@code flaggAltinn} (the DWH's own «bill on the Altinn invoice»
 * decision), {@code flaggDpv}, the DWH {@code kildetabell}, the {@code ressurs} (resource id without the
 * {@code urn:altinn:resource:} prefix) and the {@code regel} explanation. All five are null for rows from
 * older exports and from API responses without flags, which means «no flag information» — such rows are used as before.
 */
public record DwhRad(
        String organisasjonsnummer,
        String produktnavn,
        BigDecimal antall,
        BigDecimal belop,
        LocalDate dato,
        String kundenummer,
        String fakturareferanse,
        Boolean flaggAltinn,
        Boolean flaggDpv,
        String kildetabell,
        String ressurs,
        String regel) {

    /** A row without flag information (API and older exports). */
    public DwhRad(String organisasjonsnummer, String produktnavn, BigDecimal antall, BigDecimal belop,
                  LocalDate dato, String kundenummer, String fakturareferanse) {
        this(organisasjonsnummer, produktnavn, antall, belop, dato, kundenummer, fakturareferanse,
                null, null, null, null, null);
    }

    /** Whether this row comes from a flagged export. */
    public boolean erFlagget() {
        return flaggAltinn != null;
    }
}
