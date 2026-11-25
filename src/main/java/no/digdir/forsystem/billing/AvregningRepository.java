package no.digdir.forsystem.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** What a settlement run was generated with: the months it covers and the price adjustment per product (specs/020). */
@Repository
class AvregningRepository {

    private final JdbcTemplate jdbc;

    AvregningRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One covered month and the usage import its customer values and volumes came from. */
    record Maaned(LocalDate periode, Long importId) {
    }

    void lagre(Long kjoringId, List<Maaned> maaneder, Map<Long, BigDecimal> justeringer) {
        maaneder.forEach(m -> jdbc.update(
                "insert into avregning_maaned (kjoring_id, periode, import_id) values (?, ?, ?)",
                kjoringId, m.periode(), m.importId()));
        justeringer.forEach((produktId, justering) -> jdbc.update(
                "insert into avregning_prisjustering (kjoring_id, produkt_id, justering) values (?, ?, ?)",
                kjoringId, produktId, justering));
    }

    List<Maaned> maaneder(Long kjoringId) {
        return jdbc.query("select periode, import_id from avregning_maaned where kjoring_id = ? order by periode",
                (rs, i) -> new Maaned(rs.getObject("periode", LocalDate.class), rs.getLong("import_id")),
                kjoringId);
    }

    /** Product id to adjustment in kr per unit, in product code order. */
    Map<Long, BigDecimal> justeringer(Long kjoringId) {
        Map<Long, BigDecimal> ut = new LinkedHashMap<>();
        jdbc.query("""
                select j.produkt_id, j.justering from avregning_prisjustering j
                  join produkt p on p.id = j.produkt_id
                 where j.kjoring_id = ? order by p.kode""",
                rs -> {
                    ut.put(rs.getLong("produkt_id"), rs.getBigDecimal("justering"));
                }, kjoringId);
        return ut;
    }
}
