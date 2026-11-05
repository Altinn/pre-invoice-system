package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Hendelsesfilter;
import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.HendelsesloggSok;
import no.digdir.forsystem.common.Jsonb;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole event log (specs/014, Økonomi's kickoff row 24): paging, filters, Norwegian time, readable details,
 * and the complete or filtered log as XLSX/CSV, the download itself logged. Rows use the entity type TESTLOGG
 * so other rows in the shared database do not disturb the counts.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "leser", roles = "LESER")
class HendelsesloggIT extends IntegrationTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Autowired HendelsesloggRepository hendelser;
    @Autowired HendelsesloggSok sok;
    @Autowired MockMvc mvc;

    private Hendelseslogg rad(OffsetDateTime naar, String bruker, String handling, Long id, String json) {
        return hendelser.save(new Hendelseslogg(null, naar, bruker, handling, "TESTLOGG", id,
                json == null ? null : Jsonb.of(json)));
    }

    /** 120 rows, one a minute from 2026-10-01 00:00 Oslo time, alternating two users and two actions. */
    private void hundreTjue() {
        var start = LocalDate.of(2026, 10, 1).atStartOfDay(OSLO).toOffsetDateTime();
        for (int i = 0; i < 120; i++) {
            rad(start.plusMinutes(i), i % 2 == 0 ? "Kari.Test@example.no" : "ola@example.no",
                    i % 3 == 0 ? "ENDRET" : "OPPRETTET", (long) i, "{\"nr\": " + i + ", \"periode\": \"2027-01\"}");
        }
    }

    private static Hendelsesfilter testlogg() {
        return new Hendelsesfilter(null, null, null, "TESTLOGG", null);
    }

    private String side(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void pagesThroughEveryRowNewestFirst() {
        hundreTjue();

        var forste = sok.side(testlogg(), 1);
        assertThat(forste.totalt()).isEqualTo(120);
        assertThat(forste.antallSider()).isEqualTo(3);
        assertThat(forste.rader()).hasSize(50);
        assertThat(forste.rader().getFirst().entitetId()).isEqualTo(119L);
        assertThat(forste.fraRad()).isEqualTo(1);
        assertThat(forste.tilRad()).isEqualTo(50);

        var siste = sok.side(testlogg(), 3);
        assertThat(siste.rader()).hasSize(20);
        assertThat(siste.rader().getLast().entitetId()).isZero();
        assertThat(siste.fraRad()).isEqualTo(101);
        assertThat(siste.harNeste()).isFalse();
        assertThat(sok.side(testlogg(), 99).side()).isEqualTo(3); // clamped
        assertThat(sok.alle(testlogg())).hasSize(120);
    }

    @Test
    void filtersCombineAndDatesAreNorwegianDaysInclusive() {
        hundreTjue();
        // Just before and after 1 October in Oslo time: 30.09 23:59 and 02.10 00:00 Oslo.
        var foer = rad(LocalDate.of(2026, 10, 1).atStartOfDay(OSLO).minusMinutes(1).toOffsetDateTime(),
                "kari.test@example.no", "SLETTET", 900L, null);
        var etter = rad(LocalDate.of(2026, 10, 2).atStartOfDay(OSLO).toOffsetDateTime(),
                "kari.test@example.no", "SLETTET", 901L, null);

        var oktober1 = new Hendelsesfilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null, "TESTLOGG", null);
        assertThat(sok.side(oktober1, 1).totalt()).isEqualTo(120);
        var fraOgMed2 = new Hendelsesfilter(LocalDate.of(2026, 10, 2), null, null, "TESTLOGG", null);
        assertThat(sok.alle(fraOgMed2)).extracting(Hendelseslogg::id).containsExactly(etter.id());
        var tilOgMed30 = new Hendelsesfilter(null, LocalDate.of(2026, 9, 30), null, "TESTLOGG", null);
        assertThat(sok.alle(tilOgMed30)).extracting(Hendelseslogg::id).containsExactly(foer.id());

        // Part of the name, any case; % and _ are literal.
        assertThat(sok.side(new Hendelsesfilter(null, null, "KARI", "TESTLOGG", null), 1).totalt()).isEqualTo(62);
        assertThat(sok.side(new Hendelsesfilter(null, null, "%", "TESTLOGG", null), 1).totalt()).isZero();
        assertThat(sok.side(new Hendelsesfilter(null, null, "kari", "TESTLOGG", "ENDRET"), 1).totalt()).isEqualTo(20);
        assertThat(sok.side(new Hendelsesfilter(null, null, null, "TESTLOGG", "SLETTET"), 1).totalt()).isEqualTo(2);

        var ugyldig = new Hendelsesfilter(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null, null, null);
        assertThat(ugyldig.gyldig()).isFalse();
        assertThat(sok.side(ugyldig, 1).totalt()).isZero();
        assertThat(sok.entiteter()).contains("TESTLOGG");
        assertThat(sok.handlinger()).contains("ENDRET", "OPPRETTET", "SLETTET");
    }

    @Test
    void pageShowsNorwegianTimeReadableDetailsAndLinksThatKeepTheFilters() throws Exception {
        hundreTjue();
        rad(OffsetDateTime.parse("2026-11-05T09:49:13Z"), "dev", "GENERERTE", 1L,
                "{\"periode\": \"2027-01\", \"antallFakturaer\": 7}");

        String html = side("/hendelseslogg?entitet=TESTLOGG");
        assertThat(html).contains("05.11.2026 10:49:13", "periode: 2027-01; antallFakturaer: 7",
                "Viser 1–50 av 121 hendelser som passer filteret", "Side 1 av 3",
                "/hendelseslogg?entitet=TESTLOGG&amp;side=2", "/hendelseslogg.xlsx?entitet=TESTLOGG",
                "Last ned utvalget (XLSX)", "Nullstill filter");

        html = side("/hendelseslogg?entitet=TESTLOGG&handling=ENDRET&bruker=kari&side=2");
        assertThat(html).contains("Viser 1–20 av 20", "value=\"kari\"");

        assertThat(side("/hendelseslogg?entitet=FINNES_IKKE")).contains("Ingen hendelser passer filteret");
        assertThat(side("/hendelseslogg?fra=2026-10-02&til=2026-10-01")).contains("Fra-datoen er etter til-datoen");
    }

    @Test
    void xlsxHoldsEveryMatchingRowTypedAndFilterableAndTheDownloadIsLogged() throws Exception {
        hundreTjue();

        byte[] innhold = mvc.perform(get("/hendelseslogg.xlsx").param("entitet", "TESTLOGG"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andExpect(header().string("Content-Disposition", containsString("-filtrert.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(innhold))) {
            var ark = wb.getSheet("hendelseslogg");
            assertThat(ark.getLastRowNum()).isEqualTo(120);
            var hode = ark.getRow(0);
            assertThat(List.of(hode.getCell(0).getStringCellValue(), hode.getCell(5).getStringCellValue(),
                    hode.getCell(6).getStringCellValue(), hode.getCell(7).getStringCellValue()))
                    .containsExactly("tidspunkt", "periode", "detaljer", "detaljer_json");
            var forste = ark.getRow(1);
            assertThat(forste.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC); // a date-time cell
            assertThat(forste.getCell(0).getLocalDateTimeCellValue())
                    .isEqualTo(LocalDate.of(2026, 10, 1).atTime(1, 59));
            assertThat(forste.getCell(4).getNumericCellValue()).isEqualTo(119.0);
            assertThat(forste.getCell(5).getStringCellValue()).isEqualTo("2027-01");
            assertThat(forste.getCell(6).getStringCellValue()).isEqualTo("nr: 119; periode: 2027-01");
            assertThat(ark.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(ark.getPaneInformation().isFreezePane()).isTrue();
        }

        var logget = hendelser.findTop200ByOrderByTidspunktDescIdDesc().getFirst();
        assertThat(logget.handling()).isEqualTo("LASTET_NED");
        assertThat(logget.entitet()).isEqualTo("HENDELSESLOGG");
        assertThat(logget.bruker()).isEqualTo("leser");
        assertThat(logget.detaljer().json()).contains("\"antall\": 120", "\"format\": \"XLSX\"",
                "\"entitet\": \"TESTLOGG\"");
    }

    @Test
    void csvHasTheSameColumnsAndTheCompleteLogNeedsNoFilter() throws Exception {
        hundreTjue();

        String csv = new String(mvc.perform(get("/hendelseslogg.csv").param("entitet", "TESTLOGG")
                        .param("handling", "ENDRET"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> linjer = csv.lines().toList();
        assertThat(linjer.getFirst()).isEqualTo("tidspunkt,bruker,handling,entitet,entitet_id,periode,detaljer,detaljer_json");
        assertThat(linjer).hasSize(41);
        assertThat(linjer.get(1)).startsWith("2026-10-01 01:57:00,ola@example.no,ENDRET,TESTLOGG,117,2027-01,");

        long alle = sok.alle(Hendelsesfilter.ingen()).size();
        byte[] komplett = mvc.perform(get("/hendelseslogg.xlsx")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("hendelseslogg-20")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(komplett))) {
            assertThat(wb.getSheet("hendelseslogg").getLastRowNum()).isEqualTo((int) alle);
        }
    }
}
