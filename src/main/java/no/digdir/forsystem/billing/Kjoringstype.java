package no.digdir.forsystem.billing;

/** Whether a run is the ordinary monthly basis or a later selected correction. */
public enum Kjoringstype {
    ORDINAER,
    KORRIGERING,
    /** A settlement of a year's real transaction price (specs/020). */
    AVREGNING
}
