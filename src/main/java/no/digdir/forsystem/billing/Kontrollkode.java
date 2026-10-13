package no.digdir.forsystem.billing;

/** Control finding codes (docs/04 Phase 3). Stored as text in {@code kontrollfunn.kode}. */
public enum Kontrollkode {
    // BLOKKERENDE
    MANGLER_KUNDE,
    MANGLER_KUNDENUMMER,
    INAKTIV_AVTALE,
    MANGLER_PRIS,
    MANGLER_KONTERING,
    // ADVARSEL
    STORT_AVVIK,
    NULLBELOP,
    NY_KUNDE,
    /** A customer with an active agreement has no usage in the period — an expected TE may be missing (BF1 T10). */
    AKTIV_KUNDE_UTEN_BRUK
}
