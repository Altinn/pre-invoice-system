package no.digdir.forsystem.common;

import java.sql.SQLException;
import java.util.List;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Registers converters so a {@link Jsonb} maps to a PostgreSQL {@code jsonb} column and back.
 * Extending {@link AbstractJdbcConfiguration} is the supported Spring Data JDBC extension point;
 * Spring Boot uses these custom conversions.
 */
@Configuration
class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return List.of(new JsonbWritingConverter(), new JsonbReadingConverter());
    }

    @WritingConverter
    static class JsonbWritingConverter implements Converter<Jsonb, PGobject> {
        @Override
        public PGobject convert(Jsonb source) {
            var pg = new PGobject();
            pg.setType("jsonb");
            try {
                pg.setValue(source.json());
            } catch (SQLException e) {
                throw new IllegalStateException("Kunne ikke serialisere jsonb", e);
            }
            return pg;
        }
    }

    @ReadingConverter
    static class JsonbReadingConverter implements Converter<PGobject, Jsonb> {
        @Override
        public Jsonb convert(PGobject source) {
            return Jsonb.of(source.getValue());
        }
    }
}
