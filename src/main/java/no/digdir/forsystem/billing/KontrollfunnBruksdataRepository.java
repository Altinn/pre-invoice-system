package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC for the {@code kontrollfunn_bruksdata} link table: it has a composite key, which Spring
 * Data JDBC aggregates do not model well, and it is only ever bulk-written and joined.
 */
@Repository
public class KontrollfunnBruksdataRepository {

    /** A skipped usage row with the finding that explains it. */
    public record Utelatt(Long bruksdataId, String kode, String melding) {
    }

    private final JdbcTemplate jdbc;

    KontrollfunnBruksdataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lagre(List<KontrollfunnBruksdata> lenker) {
        if (lenker.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("insert into kontrollfunn_bruksdata (kontrollfunn_id, bruksdata_id) values (?, ?)",
                lenker, 500, (ps, l) -> {
                    ps.setLong(1, l.kontrollfunnId());
                    ps.setLong(2, l.bruksdataId());
                });
    }

    public List<Utelatt> forKjoring(Long kjoringId) {
        return jdbc.query("""
                select kb.bruksdata_id, k.kode, k.melding
                  from kontrollfunn_bruksdata kb
                  join kontrollfunn k on k.id = kb.kontrollfunn_id
                 where k.kjoring_id = ?
                """, (rs, i) -> new Utelatt(rs.getLong(1), rs.getString(2), rs.getString(3)), kjoringId);
    }
}
