package no.digdir.forsystem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Phase 0 acceptance: all Flyway migrations apply cleanly on a fresh PostgreSQL 16 container, and
 * the application is healthy. Runs in the {@code verify} phase (failsafe, {@code *IT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    void allMigrationsApplySuccessfully() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        // V1..V6 from docs/03, V7 for the datavarehus source (specs/001-dwh-usage-import),
        // V8 keeps superseded usage per import (specs/002-pre-approval-control D1).
        assertThat(applied).isEqualTo(8);
    }

    @Test
    void bruksdataIsUniquePerImportNotPerPeriod() {
        Integer perImport = jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = 'bruksdata_unik_per_import'", Integer.class);
        Integer perPeriode = jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = 'bruksdata_periode_organisasjonsnummer_produkt_id_type_key'",
                Integer.class);
        assertThat(perImport).isEqualTo(1);
        assertThat(perPeriode).isZero();
    }

    @Test
    void seedProductsArePresent() {
        Integer produkter = jdbc.queryForObject("select count(*) from produkt", Integer.class);
        assertThat(produkter).isEqualTo(6);
    }

    @Test
    void konteringIsNullUntilOq2() {
        // Deliberate: no invented accounting codes. Generation controls will block on NULL (OQ-2).
        Integer withKontering = jdbc.queryForObject(
                "select count(*) from produkt where konto is not null", Integer.class);
        assertThat(withKontering).isZero();
    }

    @Test
    void healthEndpointIsUp() {
        var body = rest.getForObject("/actuator/health", String.class);
        assertThat(body).contains("\"status\":\"UP\"");
    }
}
