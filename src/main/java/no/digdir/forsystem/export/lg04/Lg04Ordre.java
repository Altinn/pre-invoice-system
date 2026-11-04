package no.digdir.forsystem.export.lg04;

import java.math.BigDecimal;
import java.util.List;

/**
 * The business values for one LG04 order: a header line, then one line pair per product
 * ({@link Lg04Ordrelinje}). Raw values — {@link Lg04Skriver} does all fixed-width formatting,
 * abbreviation and byte placement. {@code ansvarlig1/2} come from configuration; {@code batchId}
 * (buntnummer) is the date the file was made, passed in by the caller (never read from the clock here).
 */
public record Lg04Ordre(
        String kundenummer,
        String fakturareferanse,
        String bestillingsnummer,
        String tekst,
        int ordreNr,
        String url,
        String ansvarlig1,
        String ansvarlig2,
        String batchId,
        List<Lg04Ordrelinje> linjer) {

    public Lg04Ordre {
        linjer = List.copyOf(linjer);
    }

    /**
     * One product on the order. Kontering ({@code konto}, {@code artikkel}, {@code dim1/2/4}) comes from
     * the product (OQ-2). {@code antall} is null for pass-through cost lines.
     */
    public record Lg04Ordrelinje(
            String konto,
            String artikkel,
            String dim1,
            String dim2,
            String dim4,
            String produktnavn,
            String belopOre,
            BigDecimal antall) {
    }
}
