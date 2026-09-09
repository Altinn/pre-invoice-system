package no.digdir.forsystem.common;

/**
 * Value wrapper for a PostgreSQL {@code jsonb} column, so the JDBC converters
 * ({@link JdbcConfig}) map only these — not every {@code String} — to/from jsonb.
 */
public record Jsonb(String json) {

    public static Jsonb of(String json) {
        return new Jsonb(json);
    }
}
