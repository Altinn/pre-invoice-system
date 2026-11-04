package no.digdir.forsystem.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Display names for the product dimensions Dim1, 2 and 4 (specs/003 D4, BF1 F01), set per environment
 * via {@code PRODUKT_DIM1_NAVN} etc. The defaults in {@code application.yaml} are the names Økonomi gave
 * (Koststed, Prosjekt/formål, Kappost; specs/010). Display only — never stored, never part of LG04.
 */
@ConfigurationProperties(prefix = "forsystem.produkt.dimensjoner")
public record Dimensjonsnavn(String dim1, String dim2, String dim4) {

    /** «Dimn», or «Dimn navn» once a name is configured. */
    public String etikett(int n) {
        String navn = switch (n) {
            case 1 -> dim1;
            case 2 -> dim2;
            case 4 -> dim4;
            default -> throw new IllegalArgumentException("Ukjent dimensjon: " + n);
        };
        return navn == null || navn.isBlank() ? "Dim" + n : "Dim" + n + " " + navn.strip();
    }
}
