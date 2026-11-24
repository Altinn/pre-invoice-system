package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the datavarehus view {@code mv_altinn_usage_monthly}: one day's usage for one
 * organisation and DWH product name. Transient — it is aggregated to monthly {@link RaaBruksrad}s and
 * only survives in the archived raw payload. {@code kundenummer}, {@code fakturareferanse},
 * {@code navn} and {@code bestillingsnummer} are the customer data CRM delivers through the DWH; forsystem keeps
 * no registry of its own (specs/018), so these go on the invoice. {@code navn} and {@code bestillingsnummer} are
 * null when the source does not deliver them.
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
        String regel,
        String navn,
        String bestillingsnummer) {

    /** A row without flag information, name or bestillingsnummer. */
    public DwhRad(String organisasjonsnummer, String produktnavn, BigDecimal antall, BigDecimal belop,
                  LocalDate dato, String kundenummer, String fakturareferanse) {
        this(organisasjonsnummer, produktnavn, antall, belop, dato, kundenummer, fakturareferanse,
                null, null, null, null, null, null, null);
    }

    /** A flagged row without name or bestillingsnummer. */
    public DwhRad(String organisasjonsnummer, String produktnavn, BigDecimal antall, BigDecimal belop,
                  LocalDate dato, String kundenummer, String fakturareferanse, Boolean flaggAltinn,
                  Boolean flaggDpv, String kildetabell, String ressurs, String regel) {
        this(organisasjonsnummer, produktnavn, antall, belop, dato, kundenummer, fakturareferanse,
                flaggAltinn, flaggDpv, kildetabell, ressurs, regel, null, null);
    }

    /** The customer values of the given rows, per orgnr (specs/018). */
    public static java.util.List<Kundedata> kundedata(java.util.List<DwhRad> rader) {
        Kundedata.Samler samler = new Kundedata.Samler();
        rader.forEach(r -> samler.legg(r.organisasjonsnummer(), r.navn(), r.kundenummer(), r.fakturareferanse(),
                r.bestillingsnummer()));
        return samler.resultat();
    }

    /** Whether this row comes from a flagged export. */
    public boolean erFlagget() {
        return flaggAltinn != null;
    }
}
