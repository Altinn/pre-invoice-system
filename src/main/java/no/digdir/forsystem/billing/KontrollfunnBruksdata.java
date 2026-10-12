package no.digdir.forsystem.billing;

/**
 * A usage row kept off the invoice and the blocking finding that kept it off (specs/002 D2). Written at
 * generation; read by the reconciliation view.
 */
public record KontrollfunnBruksdata(Long kontrollfunnId, Long bruksdataId) {
}
