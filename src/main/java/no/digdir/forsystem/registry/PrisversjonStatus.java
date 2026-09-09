package no.digdir.forsystem.registry;

/** Price-version lifecycle states (docs/03 V1, docs/04 Phase 1). */
public enum PrisversjonStatus {
    /** Editable draft; prices may be added/changed. */
    UTKAST,
    /** Live: used by generation. At most one active version per date range. */
    AKTIV,
    /** Retired; kept for traceability, never used for new runs. */
    ARKIVERT
}
