package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.util.List;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.usage.BruksdataImport;

/**
 * Imported usage against invoiced usage for one run (specs/002 US2, BF1 T06/T10): per product and type,
 * and every imported row that did not become an invoice line, with the finding that kept it off. Only
 * usage that reached forsystem is covered — what the datavarehus filters out never arrives (P-1).
 *
 * @param kilde      the usage import the run was built from; {@code null} for an old run without lines
 * @param linjerUtenImport invoice lines whose usage row is not in that import — always 0 unless defective
 */
public record Bruksdataavstemming(
        Long kjoringId,
        Periode periode,
        BruksdataImport kilde,
        List<Rad> rader,
        List<IkkeFakturert> ikkeFakturert,
        int linjerUtenImport) {

    /**
     * One product × usage type. Volume types are summed on {@code antall}, cost types on {@code belop}.
     * {@code type} is {@code null} for an active product with no usage at all this period (shown so a
     * missing product is visible).
     */
    public record Rad(String produktKode, String produktNavn, String type,
                      int importertRader, BigDecimal importertSum,
                      int fakturertLinjer, BigDecimal fakturertSum,
                      int ikkeFakturertRader) {

        /** Every imported row is either on an invoice line or listed as not invoiced. */
        public boolean balanserer() {
            return importertRader == fakturertLinjer + ikkeFakturertRader;
        }

        public boolean harDifferanse() {
            return importertRader != fakturertLinjer || importertSum.compareTo(fakturertSum) != 0;
        }

        public BigDecimal differanse() {
            return importertSum.subtract(fakturertSum);
        }
    }

    /** An imported row with no invoice line, and why (the blocking finding, or unknown for old runs). */
    public record IkkeFakturert(Long bruksdataId, String organisasjonsnummer, String produktKode, String type,
                                BigDecimal antall, BigDecimal belop, String kode, String melding) {
    }

    public boolean allFakturert() {
        return ikkeFakturert.isEmpty() && !invariantBrudd();
    }

    /** A defect: rows that are neither invoiced nor explained, or lines that point outside the import. */
    public boolean invariantBrudd() {
        return linjerUtenImport > 0 || rader.stream().anyMatch(r -> !r.balanserer());
    }
}
