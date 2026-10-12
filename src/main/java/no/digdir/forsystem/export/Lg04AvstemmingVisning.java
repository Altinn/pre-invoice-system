package no.digdir.forsystem.export;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import no.digdir.forsystem.billing.Lg04Avstemming;

/**
 * The LG04 reconciliation as the run page shows it (specs/002 D6, FR-006/007). {@code kilde} says where
 * it came from: stored at export, computed now from the archived file (runs exported before
 * reconciliation existed, or a manual re-check), or a failure to read or trust the archived file — which
 * is shown as an error, never as a match.
 */
public record Lg04AvstemmingVisning(
        Kilde kilde,
        Integer antallOrdrerFil,
        Long sumOreFil,
        Integer antallOrdrerKjoring,
        Long sumOreKjoring,
        Integer antallFakturaer,
        String sha256,
        String avstemtAv,
        OffsetDateTime avstemtAt,
        String feil) {

    public enum Kilde { LAGRET, BEREGNET_FRA_ARKIV, FEIL }

    static Lg04AvstemmingVisning lagret(Lg04Avstemming a) {
        return new Lg04AvstemmingVisning(Kilde.LAGRET, a.antallOrdrerFil(), a.sumOreFil(), a.antallOrdrerKjoring(),
                a.sumOreKjoring(), a.antallFakturaer(), a.sha256(), a.avstemtAv(), a.avstemtAt(), null);
    }

    static Lg04AvstemmingVisning fraArkiv(int ordrerFil, long oreFil, int ordrerKjoring, long oreKjoring,
                                          int fakturaer, String sha256) {
        return new Lg04AvstemmingVisning(Kilde.BEREGNET_FRA_ARKIV, ordrerFil, oreFil, ordrerKjoring, oreKjoring,
                fakturaer, sha256, null, null, null);
    }

    static Lg04AvstemmingVisning feil(String grunn) {
        return new Lg04AvstemmingVisning(Kilde.FEIL, null, null, null, null, null, null, null, null, grunn);
    }

    public boolean stemmer() {
        return kilde != Kilde.FEIL
                && antallOrdrerFil.equals(antallOrdrerKjoring) && sumOreFil.equals(sumOreKjoring);
    }

    public BigDecimal belopFil() {
        return sumOreFil == null ? null : BigDecimal.valueOf(sumOreFil, 2);
    }

    public BigDecimal belopKjoring() {
        return sumOreKjoring == null ? null : BigDecimal.valueOf(sumOreKjoring, 2);
    }
}
