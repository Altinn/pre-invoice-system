package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * An invoice in a run with at least one line without fakturareferanse («Deres referanse») — one row of the
 * list on the Kontroller tab (specs/012 FR-003), read from the lines' snapshot so it matches the
 * MANGLER_FAKTURAREFERANSE findings and the LG04 file.
 *
 * @param produkter     names of the products whose lines lack the reference, in line order
 * @param antallLinjer  lines without fakturareferanse
 * @param alleLinjer    true when no line of the invoice has a fakturareferanse
 */
public record FakturaUtenReferanse(
        Long fakturaId,
        Integer ordreNr,
        String kundenavn,
        String organisasjonsnummer,
        String kundenummer,
        List<String> produkter,
        int antallLinjer,
        boolean alleLinjer,
        BigDecimal sumBelop) {
}
