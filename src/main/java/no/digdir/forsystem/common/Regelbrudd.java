package no.digdir.forsystem.common;

/**
 * A business rule was violated (e.g. activating a price version that is missing a required price).
 * Carries a human-readable Norwegian message shown to the user; the web layer renders it as a form
 * or page error rather than a stack trace.
 */
public class Regelbrudd extends RuntimeException {

    public Regelbrudd(String melding) {
        super(melding);
    }
}
