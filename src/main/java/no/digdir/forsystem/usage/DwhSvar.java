package no.digdir.forsystem.usage;

import java.time.OffsetDateTime;

/**
 * A complete datavarehus response for one period: the exact bytes received (archived for
 * traceability) and when they were fetched. Parse with {@link DwhRespons#les(byte[])}.
 */
public record DwhSvar(byte[] raadata, OffsetDateTime hentetAt) {
}
