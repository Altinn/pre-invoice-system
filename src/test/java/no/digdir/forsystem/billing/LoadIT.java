package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Periode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Load sanity (docs/04 Phase 5): 5000 customers × 6 products generates in under a minute. Opt-in —
 * runs only with {@code -Dload=true} so it stays out of the normal build:
 *
 * <pre>./mvnw failsafe:integration-test -Dit.test=LoadIT -Dload=true</pre>
 */
@EnabledIfSystemProperty(named = "load", matches = "true")
class LoadIT extends IntegrationTest {

    private static final Periode JAN = Periode.av(2027, 1);

    @Autowired JdbcTemplate jdbc;
    @Autowired FakturakjoringService tjeneste;

    @Test
    void generates5000CustomersUnderOneMinute() {
        jdbc.update("update produkt set konto='DEMOKONTO', artikkel_id=1, dim_1='a', dim_2='b', dim_4='c'");
        jdbc.update("insert into prisversjon(navn,gyldig_fra,gyldig_til,status) "
                + "values ('Load','2027-01-01',null,'AKTIV')");
        Long vid = jdbc.queryForObject("select id from prisversjon where navn='Load'", Long.class);
        jdbc.update("insert into pris(prisversjon_id,produkt_id,enhetspris) select ?, id, 1.0 from produkt", vid);
        jdbc.update("insert into kunde(organisasjonsnummer,virksomhetsnavn,avtalestatus,kilde,oppdatert_av) "
                + "select (100000000+g)::text, 'Kunde '||g, 'AKTIV', 'MANUELL', 'load' "
                + "from generate_series(0,4999) g");
        jdbc.update("insert into kundenummer_regel(kunde_id,kundenummer) "
                + "select id, 'KN-'||organisasjonsnummer from kunde");
        jdbc.update("insert into bruksdata_import(filnavn,periode,kilde,status,antall_rader,lastet_av) "
                + "values ('load.csv','2027-01-01','CSV','VALIDERT',30000,'load')");
        Long imp = jdbc.queryForObject("select id from bruksdata_import where filnavn='load.csv'", Long.class);
        jdbc.update("insert into bruksdata(import_id,periode,organisasjonsnummer,produkt_id,type,antall) "
                + "select ?, date '2027-01-01', k.organisasjonsnummer, p.id, 'BRUKSVOLUM', 100.00 "
                + "from kunde k cross join produkt p", imp);

        long start = System.nanoTime();
        var kj = tjeneste.generer(JAN);
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(tjeneste.fakturaer(kj.id())).hasSize(5000);
        System.out.println("[load] genererte 5000 fakturaer (30000 linjer) på " + ms + " ms");
        assertThat(ms).isLessThan(60_000);
    }
}
