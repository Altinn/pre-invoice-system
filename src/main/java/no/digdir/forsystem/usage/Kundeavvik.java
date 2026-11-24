package no.digdir.forsystem.usage;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One customer (orgnr) with at least one deviation on a comparison (specs/019). The {@code fakturert*} arrays
 * hold the distinct values the customer was last invoiced with ({@code ""} = none); the {@code kandidat*}
 * values are what the new DWH snapshot delivered ({@code null} = the candidate has no row for the customer).
 * Insert-only, like the comparison it belongs to.
 */
@Table("bruksdata_kundeavvik")
public record Kundeavvik(
        @Id Long id,
        Long sammenligningId,
        String organisasjonsnummer,
        String[] fakturertNavn,
        String[] fakturertKundenummer,
        String[] fakturertFakturareferanse,
        String[] fakturertBestillingsnummer,
        String kandidatNavn,
        String kandidatKundenummer,
        String kandidatFakturareferanse,
        String kandidatBestillingsnummer,
        String[] kategorier) {

    public List<Kundeavvikskategori> kategoriliste() {
        return Arrays.stream(kategorier).map(Kundeavvikskategori::valueOf).toList();
    }

    public boolean har(Kundeavvikskategori kategori) {
        return Arrays.asList(kategorier).contains(kategori.name());
    }

    /** Customer-level changes a delta correction cannot express (FR-011). */
    public List<Kundeavvikskategori> hindrerDifferanse() {
        return kategoriliste().stream().filter(Kundeavvikskategori::hindrerDifferanse).toList();
    }

    /** The name to show: the candidate's, else the invoiced one, else the orgnr (never an invented name). */
    public String navn() {
        if (kandidatNavn != null && !kandidatNavn.isBlank()) {
            return kandidatNavn;
        }
        return forste(fakturertNavn, organisasjonsnummer);
    }

    /** The U4 kundenummer to show: the candidate's, else the invoiced one; null if neither has one. */
    public String kundenummer() {
        if (kandidatKundenummer != null && !kandidatKundenummer.isBlank()) {
            return kandidatKundenummer;
        }
        return forste(fakturertKundenummer, null);
    }

    private static String forste(String[] verdier, String ellers) {
        return Arrays.stream(verdier).filter(v -> !v.isBlank()).findFirst().orElse(ellers);
    }
}
