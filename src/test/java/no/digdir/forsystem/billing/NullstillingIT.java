package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.usage.UsageImportService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Nullstill periode" (test environments only): a month that went all the way to export can be wiped
 * and run again end to end with the same usage file, without touching other months.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "forsystem.testverktoy.enabled=true")
class NullstillingIT extends BillingFixture {

    private static final Periode FEB = Periode.av(2027, 2);
    private static final String CSV = """
            periode,organisasjonsnummer,produktkode,type,antall,belop
            %s,958935420,melding,BRUKSVOLUM,1000,
            """;

    @Autowired NullstillingService nullstilling;
    @Autowired UsageImportService usage;
    @Autowired EksportService eksport;
    @Autowired HendelsesloggRepository hendelser;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private void registre() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "0.85");
        Long k = kunde("958935420", "Oslo kommune", "AKTIV", null).id();
        regel(k, "KN-1001", null, null, null);
    }

    private void importer(Periode periode) throws IOException {
        byte[] fil = CSV.formatted(periode.førsteDag()).getBytes(StandardCharsets.UTF_8);
        usage.importer(usage.forhaandsvis(periode, "bruk.csv", new ByteArrayInputStream(fil)));
    }

    private Fakturakjoring eksportert(Periode periode) throws IOException {
        importer(periode);
        var kj = tjeneste.generer(periode);
        tjeneste.godkjenn(kj.id());
        eksport.eksporter(kj.id());
        return kj;
    }

    private int antall(String tabell, String hvor, Object... args) {
        return jdbc.queryForObject("select count(*) from " + tabell + " where " + hvor, Integer.class, args);
    }

    @Test
    void wipesAnExportedMonthSoTheSameFileCanBeRunAgain() throws IOException {
        registre();
        importer(JAN); // superseded below: AVVIST imports go too
        var jan = eksportert(JAN);
        var feb = eksportert(FEB);
        List<Path> filer = tjeneste.eksportfiler(jan.id()).stream().map(f -> Path.of(f.blobUrl())).toList();
        assertThat(filer).isNotEmpty().allSatisfy(f -> assertThat(f).exists());

        var nullstilt = nullstilling.nullstill(JAN);

        assertThat(nullstilt.kjoringer()).isEqualTo(1);
        assertThat(nullstilt.importer()).isEqualTo(2);
        assertThat(nullstilt.fakturaer()).isEqualTo(1);
        assertThat(nullstilt.filer()).isEqualTo(filer.size());
        assertThat(antall("fakturakjoring", "periode = ?", JAN.førsteDag())).isZero();
        assertThat(antall("bruksdata_import", "periode = ?", JAN.førsteDag())).isZero();
        assertThat(antall("bruksdata", "periode = ?", JAN.førsteDag())).isZero();
        assertThat(antall("faktura", "kjoring_id = ?", jan.id())).isZero();
        assertThat(antall("eksportfil", "kjoring_id = ?", jan.id())).isZero();
        assertThat(antall("lg04_avstemming", "kjoring_id = ?", jan.id())).isZero();

        // February is untouched.
        assertThat(tjeneste.hent(feb.id()).status()).isEqualTo("EKSPORTERT");
        assertThat(antall("lg04_avstemming", "kjoring_id = ?", feb.id())).isEqualTo(1);

        // The deletion itself stays in the audit trail.
        var logg = hendelser.findTop200ByOrderByTidspunktDescIdDesc().getFirst();
        assertThat(logg.handling()).isEqualTo("NULLSTILTE");
        assertThat(logg.detaljer().json()).contains("\"periode\": \"2027-01\"", "\"kjoringer\": [" + jan.id() + "]");

        // Archived files go only after commit (the test transaction rolls back, so run the hook here).
        assertThat(filer).allSatisfy(f -> assertThat(f).exists());
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getEnclosingClass() == NullstillingService.class)
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(filer).allSatisfy(f -> assertThat(f).doesNotExist());

        // The tester's loop: same file, same month, all the way again.
        var igjen = eksportert(JAN);
        assertThat(tjeneste.hent(igjen.id()).status()).isEqualTo("EKSPORTERT");
    }

    @Test
    void wipesFindingsOfARunWithSkippedUsage() throws IOException {
        registre();
        importer(JAN);
        jdbc.update("delete from kundenummer_regel"); // usage without a kundenummer is skipped and linked
        var kj = tjeneste.generer(JAN);
        assertThat(antall("kontrollfunn", "kjoring_id = ?", kj.id())).isPositive();

        nullstilling.nullstill(JAN);

        assertThat(antall("kontrollfunn", "kjoring_id = ?", kj.id())).isZero();
        assertThat(antall("fakturakjoring", "periode = ?", JAN.førsteDag())).isZero();
    }

    @Test
    void listsMonthsWithDataAndRefusesAnEmptyOne() throws IOException {
        registre();
        eksportert(JAN);
        importer(FEB);

        assertThat(nullstilling.perioderMedData()).contains(
                new NullstillingService.Periodedata(FEB, 0, 1, false),
                new NullstillingService.Periodedata(JAN, 1, 1, true));
        assertThatThrownBy(() -> nullstilling.nullstill(Periode.av(2027, 3)))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("mars 2027");
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void forvalterResetsFromTheRunList() throws Exception {
        registre();
        importer(JAN);
        mvc.perform(get("/kjoringer"))
                .andExpect(content().string(Matchers.containsString("Testverktøy: nullstill periode")));

        mvc.perform(post("/testverktoy/nullstill-periode").with(csrf()).param("periode", "2027-01"))
                .andExpect(redirectedUrl("/kjoringer"))
                .andExpect(flash().attribute("nullstilt", Matchers.startsWith("Januar 2027 er nullstilt.")));
        assertThat(antall("bruksdata_import", "periode = ?", JAN.førsteDag())).isZero();
    }

    @Test
    @WithMockUser(roles = "LESER")
    void leserCannotReset() throws Exception {
        mvc.perform(post("/testverktoy/nullstill-periode").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().isForbidden());
    }
}
