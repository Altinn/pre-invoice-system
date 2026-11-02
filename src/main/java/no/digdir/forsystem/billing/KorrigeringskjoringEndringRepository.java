package no.digdir.forsystem.billing;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access to the immutable selected-change join table. */
@Repository
public class KorrigeringskjoringEndringRepository {

    private final JdbcTemplate jdbc;

    KorrigeringskjoringEndringRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lagre(Long kjoringId, List<Long> endringIder, String valgtAv, OffsetDateTime valgtAt) {
        jdbc.batchUpdate("""
                insert into korrigeringskjoring_endring (kjoring_id, endring_id, valgt_av, valgt_at)
                values (?, ?, ?, ?)
                """, endringIder, 500, (ps, endringId) -> {
            ps.setLong(1, kjoringId);
            ps.setLong(2, endringId);
            ps.setString(3, valgtAv);
            ps.setObject(4, valgtAt);
        });
    }

    public List<Long> endringIder(Long kjoringId) {
        return jdbc.queryForList("""
                select endring_id from korrigeringskjoring_endring
                 where kjoring_id = ? order by endring_id
                """, Long.class, kjoringId);
    }

    /** Selected changes that have actually been exported, in correction-version order. */
    public List<Long> eksporterteEndringIder(LocalDate periode) {
        return jdbc.queryForList("""
                select ke.endring_id
                  from korrigeringskjoring_endring ke
                  join fakturakjoring k on k.id = ke.kjoring_id
                 where k.periode = ?
                   and k.type = 'KORRIGERING'
                   and k.status = 'EKSPORTERT'
                 order by k.versjon, ke.endring_id
                """, Long.class, periode);
    }
}
