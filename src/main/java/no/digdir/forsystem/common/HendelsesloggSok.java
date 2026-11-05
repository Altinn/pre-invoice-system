package no.digdir.forsystem.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the whole audit trail with the page's filters (specs/014): one page at a time for the screen, or every
 * matching row for a download. Newest first, as on the page. Read-only.
 */
@Service
public class HendelsesloggSok {

    public static final int SIDESTORRELSE = 50;

    private static final RowMapper<Hendelseslogg> RAD = HendelsesloggSok::rad;

    private final NamedParameterJdbcTemplate jdbc;

    HendelsesloggSok(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of matching rows and the total. {@code side} starts at 1 and is clamped to the last page. */
    public record Side(List<Hendelseslogg> rader, int side, int antallSider, long totalt) {

        public long fraRad() {
            return totalt == 0 ? 0 : (long) (side - 1) * SIDESTORRELSE + 1;
        }

        public long tilRad() {
            return fraRad() == 0 ? 0 : fraRad() + rader.size() - 1;
        }

        public boolean harForrige() {
            return side > 1;
        }

        public boolean harNeste() {
            return side < antallSider;
        }
    }

    @Transactional(readOnly = true)
    public Side side(Hendelsesfilter filter, int onsketSide) {
        if (!filter.gyldig()) {
            return new Side(List.of(), 1, 1, 0);
        }
        var params = new MapSqlParameterSource();
        String hvor = hvor(filter, params);
        long totalt = jdbc.queryForObject("select count(*) from hendelseslogg" + hvor, params, Long.class);
        int antallSider = (int) Math.max(1, (totalt + SIDESTORRELSE - 1) / SIDESTORRELSE);
        int side = Math.min(Math.max(1, onsketSide), antallSider);
        params.addValue("grense", SIDESTORRELSE).addValue("forskyvning", (long) (side - 1) * SIDESTORRELSE);
        List<Hendelseslogg> rader = jdbc.query(UTVALG + hvor + REKKEFOLGE + " limit :grense offset :forskyvning",
                params, RAD);
        return new Side(rader, side, antallSider, totalt);
    }

    /** Every matching row, newest first — the download. */
    @Transactional(readOnly = true)
    public List<Hendelseslogg> alle(Hendelsesfilter filter) {
        if (!filter.gyldig()) {
            return List.of();
        }
        var params = new MapSqlParameterSource();
        return jdbc.query(UTVALG + hvor(filter, params) + REKKEFOLGE, params, RAD);
    }

    /** The entity types in the log, for the filter's list. */
    @Transactional(readOnly = true)
    public List<String> entiteter() {
        return jdbc.queryForList("select distinct entitet from hendelseslogg where entitet is not null order by 1",
                new MapSqlParameterSource(), String.class);
    }

    /** The actions in the log, for the filter's list. */
    @Transactional(readOnly = true)
    public List<String> handlinger() {
        return jdbc.queryForList("select distinct handling from hendelseslogg order by 1",
                new MapSqlParameterSource(), String.class);
    }

    private static final String UTVALG =
            "select id, tidspunkt, bruker, handling, entitet, entitet_id, detaljer::text as detaljer from hendelseslogg";
    private static final String REKKEFOLGE = " order by tidspunkt desc, id desc";

    private static String hvor(Hendelsesfilter f, MapSqlParameterSource params) {
        List<String> vilkaar = new ArrayList<>();
        if (f.fra() != null) {
            vilkaar.add("tidspunkt >= :fra");
            params.addValue("fra", f.fraTidspunkt());
        }
        if (f.til() != null) {
            vilkaar.add("tidspunkt < :til");
            params.addValue("til", f.tilTidspunkt());
        }
        if (f.bruker() != null) {
            vilkaar.add("bruker ilike :bruker escape '\\'");
            params.addValue("bruker", "%" + f.bruker().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%");
        }
        if (f.entitet() != null) {
            vilkaar.add("entitet = :entitet");
            params.addValue("entitet", f.entitet());
        }
        if (f.handling() != null) {
            vilkaar.add("handling = :handling");
            params.addValue("handling", f.handling());
        }
        return vilkaar.isEmpty() ? "" : " where " + String.join(" and ", vilkaar);
    }

    private static Hendelseslogg rad(ResultSet rs, int n) throws SQLException {
        long id = rs.getLong("entitet_id");
        Long entitetId = rs.wasNull() ? null : id;
        String detaljer = rs.getString("detaljer");
        return new Hendelseslogg(rs.getLong("id"), rs.getObject("tidspunkt", OffsetDateTime.class),
                rs.getString("bruker"), rs.getString("handling"), rs.getString("entitet"),
                entitetId, detaljer == null ? null : Jsonb.of(detaljer));
    }
}
