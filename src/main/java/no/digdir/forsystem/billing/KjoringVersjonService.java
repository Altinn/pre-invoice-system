package no.digdir.forsystem.billing;

import java.sql.PreparedStatement;
import java.time.LocalDate;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Serializes run/version allocation per period inside the caller's transaction. */
@Service
class KjoringVersjonService {

    private static final long LOCK_NAMESPACE = 4_607_000_000_000_000L;

    private final JdbcTemplate jdbc;
    private final FakturakjoringRepository kjoringer;

    KjoringVersjonService(JdbcTemplate jdbc, FakturakjoringRepository kjoringer) {
        this.jdbc = jdbc;
        this.kjoringer = kjoringer;
    }

    void laas(LocalDate periode) {
        long nokkel = LOCK_NAMESPACE + periode.toEpochDay();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, nokkel);
                statement.execute();
            }
            return null;
        });
    }

    int neste(LocalDate periode) {
        return kjoringer.nesteVersjon(periode);
    }
}
