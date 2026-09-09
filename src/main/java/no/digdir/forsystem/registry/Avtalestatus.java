package no.digdir.forsystem.registry;

/** Agreement status (docs/03 V2). Must be AKTIV to invoice; INAKTIV is BLOKKERENDE in generation. */
public enum Avtalestatus {
    AKTIV,
    INAKTIV,
    UNDER_AVKLARING
}
