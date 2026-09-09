package no.digdir.forsystem.export.lg04;

/**
 * The business values for one LG04 order (three lines). Raw values — {@link Lg04Skriver} does all
 * fixed-width formatting, abbreviation and byte placement. Kontering ({@code konto}, {@code artikkel},
 * {@code dim1/2/4}) comes from the product (OQ-2); {@code ansvarlig1/2} from configuration;
 * {@code batchId} from the run (never the clock).
 */
public record Lg04Ordre(
        String konto,
        String artikkel,
        String dim1,
        String dim2,
        String dim4,
        String kundenummer,
        String fakturareferanse,
        String bestillingsnummer,
        String beskrivelse,
        String belopOre,
        int ordreNr,
        String url,
        String ansvarlig1,
        String ansvarlig2,
        String batchId,
        int sekvensnr) {
}
