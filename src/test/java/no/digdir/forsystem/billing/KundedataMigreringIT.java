package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.sql.DataSource;

import no.digdir.forsystem.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * specs/018: a database with customers, invoices and threshold rows at V16 migrates to V17 (customer values
 * per import, orgnr and name on the invoice) and V18 (registry gone) without losing what old runs show.
 * Runs in its own schema so the shared, fully migrated schema is untouched.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KundedataMigreringIT extends IntegrationTest {

    private static final String S = "mig018";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(S).defaultSchema(S)
                .locations("classpath:db/migration").target(target).load();
    }

    @AfterEach
    void ryddOpp() {
        jdbc.execute("drop schema if exists " + S + " cascade");
    }

    @Test
    void oldRunsKeepTheirCustomerValuesAndTheRegistryIsDropped() {
        flyway("16").migrate();

        Long kunde = id("insert into %s.kunde (organisasjonsnummer, virksomhetsnavn, oppdatert_av) "
                + "values ('100000001', 'Alfa kommune', 'test') returning id");
        jdbc.update(q("insert into %s.kundenummer_regel (kunde_id, kundenummer, produkt_id) "
                + "values (?, 'KN-PROD', (select id from %s.produkt where kode = 'melding'))"), kunde);
        jdbc.update(q("insert into %s.kundenummer_regel (kunde_id, kundenummer) values (?, 'KN-1')"), kunde);
        jdbc.update(q("insert into %s.kunde_referanse (kunde_id, fakturareferanse, bestillingsnummer) "
                + "values (?, 'REF-A', 'PO-1')"), kunde);

        Long imp = id("insert into %s.bruksdata_import (filnavn, periode, status, lastet_av) "
                + "values ('t.csv', '2027-01-01', 'VALIDERT', 'test') returning id");
        jdbc.update(q("insert into %s.bruksdata (import_id, periode, organisasjonsnummer, produkt_id, type, antall) "
                + "values (?, '2027-01-01', '100000001', (select id from %s.produkt where kode = 'melding'), "
                + "'BRUKSVOLUM', 10)"), imp);
        jdbc.update(q("insert into %s.bruksdata (import_id, periode, organisasjonsnummer, produkt_id, type, antall) "
                + "values (?, '2027-01-01', '999999999', (select id from %s.produkt where kode = 'melding'), "
                + "'BRUKSVOLUM', 5)"), imp);

        Long versjon = id("insert into %s.prisversjon (navn, gyldig_fra, status) values ('P', '2027-01-01', 'AKTIV') "
                + "returning id");
        Long kj = id("insert into %s.fakturakjoring (periode, status, prisversjon_id, generert_av, versjon, "
                + "bruksdata_import_id) values ('2027-01-01', 'FORKASTET', " + versjon + ", 'test', 1, " + imp
                + ") returning id");
        // An invoice from before the name was frozen (V11): kundenavn NULL.
        jdbc.update(q("insert into %s.faktura (kjoring_id, kunde_id, faktura_uuid, ordre_nr, kundenummer, "
                + "fakturamottaker_orgnr, sum_belop) values (?, ?, gen_random_uuid(), 1, 'KN-1', '100000001', 10)"),
                kj, kunde);
        Long funn = id("insert into %s.kontrollfunn (kjoring_id, alvorlighet, kode, melding) "
                + "values (" + kj + ", 'ADVARSEL', 'UNDER_TERSKEL', 'x') returning id");
        jdbc.update(q("insert into %s.faktura_under_terskel (kjoring_id, kunde_id, organisasjonsnummer, kundenavn, "
                + "kundenummer, sum_belop, antall_linjer, kundesum, terskelverdi, kontrollfunn_id) "
                + "values (?, ?, '100000001', 'Alfa kommune', 'KN-1', 1, 1, 1, 40, ?)"), kj, kunde, funn);

        flyway("17").migrate();

        Map<String, Object> faktura = jdbc.queryForMap(q("select organisasjonsnummer, kundenavn from %s.faktura"));
        assertThat(faktura).containsEntry("organisasjonsnummer", "100000001").containsEntry("kundenavn", "Alfa kommune");
        Map<String, Object> alfa = jdbc.queryForMap(q("select * from %s.bruksdata_kunde where organisasjonsnummer = "
                + "'100000001'"));
        // The rule without product wins over the product rule; the default reference comes along.
        assertThat(alfa).containsEntry("kundenummer", "KN-1").containsEntry("fakturareferanse", "REF-A")
                .containsEntry("bestillingsnummer", "PO-1").containsEntry("virksomhetsnavn", "Alfa kommune");
        Map<String, Object> ukjent = jdbc.queryForMap(q("select * from %s.bruksdata_kunde where organisasjonsnummer = "
                + "'999999999'"));
        assertThat(ukjent.get("kundenummer")).isNull();   // gives MANGLER_KUNDENUMMER, as MANGLER_KUNDE did

        flyway("18").migrate();

        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? "
                + "and table_name in ('kunde', 'kunde_referanse', 'kundenummer_regel')", Integer.class, S)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ? "
                + "and column_name = 'kunde_id'", Integer.class, S)).isZero();
        assertThat(jdbc.queryForObject(q("select kundenavn from %s.faktura"), String.class)).isEqualTo("Alfa kommune");
        assertThat(jdbc.queryForObject(q("select count(*) from %s.faktura_under_terskel"), Integer.class)).isEqualTo(1);
    }

    private Long id(String sql) {
        return jdbc.queryForObject(q(sql), Long.class);
    }

    private static String q(String sql) {
        return sql.replace("%s", S);
    }
}
