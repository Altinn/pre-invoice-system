package no.digdir.forsystem.usage;

import java.math.BigDecimal;

/** Per-product totals for the staging report, so the user can eyeball them against the file. */
public record ProduktSum(String produktkode, long antallRader, BigDecimal sumAntall, BigDecimal sumBelop) {
}
