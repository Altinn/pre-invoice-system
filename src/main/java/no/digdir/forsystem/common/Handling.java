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
    /** A complete correction candidate was compared with one exported run baseline. */
    SAMMENLIGNET,
    EKSPORTERTE,
    /** An export was stopped because the LG04 file did not reconcile with the run; nothing was written. */
    EKSPORT_STOPPET,
    /** A fakturagrunnlag spreadsheet was downloaded (also before approval); changes nothing. */
    LASTET_NED,
    /** One or more warnings of a run were accepted («Funn akseptert», specs/013). */
    AKSEPTERTE_FUNN,
    /** The acceptance of one or more warnings was removed again (specs/013). */
    ANGRET_AKSEPT,
    /** Test environments only: every run and usage import of a period was permanently deleted. */
    NULLSTILTE
}
