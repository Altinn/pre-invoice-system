package no.digdir.forsystem.usage;

import java.time.OffsetDateTime;

/**
 * The raw source payload behind a datavarehus import. {@code url}/{@code sha256} are null until the
 * payload has been archived (on commit, or immediately when staged).
 */
public record Raadata(byte[] innhold, OffsetDateTime hentetAt, String url, String sha256) {

    public boolean erArkivert() {
        return url != null;
    }
}
