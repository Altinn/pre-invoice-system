package no.digdir.forsystem.common;

/**
 * The kind of state change recorded in {@code hendelseslogg}. Kept generic (the verb); the affected
 * entity type and id are separate columns. Phase 1 covers registry maintenance; later phases add
 * import/generate/approve/export actions.
 */
public enum Handling {
    OPPRETTET,
    ENDRET,
    SLETTET,
    AKTIVERTE,
    ARKIVERTE,
    IMPORTERTE,
    AVVISTE,
    GENERERTE,
    GODKJENTE,
    FORKASTET,
    HENTET,
    EKSPORTERTE,
    /** An export was stopped because the LG04 file did not reconcile with the run; nothing was written. */
    EKSPORT_STOPPET,
    /** A fakturagrunnlag spreadsheet was downloaded (also before approval); changes nothing. */
    LASTET_NED,
    /** Test environments only: every run and usage import of a period was permanently deleted. */
    NULLSTILTE
}
