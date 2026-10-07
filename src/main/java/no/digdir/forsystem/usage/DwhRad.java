package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the datavarehus view {@code mv_altinn_usage_monthly}: one day's usage for one
 * organisation and DWH product name. Transient — it is aggregated to monthly {@link RaaBruksrad}s and
 * only survives in the archived raw payload. {@code kundenummer}/{@code fakturareferanse} are the
 * DWH's copy of customer data, used for cross-checking only (the registry is the master).
 */
public record DwhRad(
        String organisasjonsnummer,
        String produktnavn,
        BigDecimal antall,
        BigDecimal belop,
        LocalDate dato,
        String kundenummer,
        String fakturareferanse) {
}
