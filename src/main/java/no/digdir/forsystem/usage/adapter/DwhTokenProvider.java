package no.digdir.forsystem.usage.adapter;

/** Supplies a bearer token for the datavarehus API when it requires authentication (OQ-19). */
interface DwhTokenProvider {

    String token();
}
