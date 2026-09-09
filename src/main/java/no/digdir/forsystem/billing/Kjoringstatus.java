package no.digdir.forsystem.billing;

/** Invoicing-run lifecycle (docs/03 V4, docs/04 Phase 3). One-way except FORKASTET. */
public enum Kjoringstatus {
    GENERERT,
    GODKJENT,
    EKSPORTERT,
    FORKASTET
}
