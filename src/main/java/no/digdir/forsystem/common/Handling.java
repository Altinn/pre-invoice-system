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
    EKSPORTERTE
}
