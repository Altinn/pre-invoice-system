package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.registry.Kunde;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The fakturagrunnlag spreadsheet before approval (specs/002 US3, FR-014..019, FR-015a, SC-005).
 */
@AutoConfigureMockMvc
@WithMockUser(username = "leser", roles = "LESER")
class FakturagrunnlagRegnearkIT extends BillingFixture {

    private static final List<String> KOLONNER = List.of("periode", "ordre", "kundenummer", "kundenavn",
            "organisasjonsnummer", "fakturamottaker", "tilleggstekst", "produkt", "type", "beskrivelse",
            "servicekode", "fakturareferanse", "bestillingsnummer", "antall", "enhetspris", "belop",
            "bruksdata_id", "pris_id");

    @Autowired MockMvc mvc;
    @Autowired EksportService eksport;
    @Autowired EksportfilRepository eksportfiler;
    @Autowired HendelsesloggRepository hendelser;
    @Autowired FileArchive arkiv;
    @Autowired JdbcTemplate jdbc;

    private Long kundeId;

    /** One customer: melding 3 × 1.25 = 3.75, SMS cost 12.50 → total 16.25. */
    private Fakturakjoring generert() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.2500");
        kundeId = kunde("100000001", "Oslo kommune", "AKTIV", null).id();
        regel(kundeId, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        bruk(imp, JAN, "100000001", "varsling", Bruksdatatype.SMS_KOSTNAD, null, "12.50");
        return tjeneste.generer(JAN);
    }

    @Test
    void xlsxBeforeApprovalHasTypedColumnsThatSumToTheRunTotal() throws Exception {
        var kj = generert();

        byte[] xlsx = mvc.perform(get("/kjoringer/{id}/fakturagrunnlag.xlsx", kj.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("fakturagrunnlag-2027-01-GENERERT.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet ark = wb.getSheetAt(0);
            assertThat(hode(ark)).isEqualTo(KOLONNER);
            assertThat(ark.getLastRowNum()).isEqualTo(2);   // two lines
            int belop = KOLONNER.indexOf("belop");
            BigDecimal sum = BigDecimal.ZERO;
            for (int r = 1; r <= ark.getLastRowNum(); r++) {
                Row rad = ark.getRow(r);
                assertThat(rad.getCell(belop).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(rad.getCell(KOLONNER.indexOf("kundenavn")).getStringCellValue()).isEqualTo("Oslo kommune");
                sum = sum.add(BigDecimal.valueOf(rad.getCell(belop).getNumericCellValue()));
            }
            assertThat(sum).isEqualByComparingTo(tjeneste.sammendrag(kj.id()).sumBelop()).isEqualByComparingTo("16.25");
            Row melding = ark.getRow(1);
            assertThat(melding.getCell(KOLONNER.indexOf("type")).getStringCellValue()).isEqualTo("BRUKSVOLUM");
            assertThat(melding.getCell(KOLONNER.indexOf("antall")).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(melding.getCell(KOLONNER.indexOf("enhetspris")).getNumericCellValue()).isEqualTo(1.25);
        }

        // Nothing changed, nothing recorded as an export file, the download is logged.
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GENERERT");
        assertThat(eksportfiler.findByKjoringIdOrderByType(kj.id())).isEmpty();
        assertThat(hendelser.findAll()).anySatisfy(h -> {
            assertThat(h.handling()).isEqualTo("LASTET_NED");
            assertThat(h.detaljer().json()).contains("XLSX").contains("GENERERT");
        });
    }

    @Test
    void csvHasTheSameColumns() throws Exception {
        var kj = generert();
        String csv = mvc.perform(get("/kjoringer/{id}/fakturagrunnlag.csv", kj.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.lines().findFirst().orElseThrow()).isEqualTo(String.join(",", KOLONNER));
        assertThat(csv).contains("Oslo kommune").contains("12.50");
    }

    @Test
    void discardedRunIsRefused() throws Exception {
        var kj = generert();
        tjeneste.forkast(kj.id());
        mvc.perform(get("/kjoringer/{id}/fakturagrunnlag.xlsx", kj.id()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("Forkastede kjøringer kan ikke lastes ned")));
    }

    @Test
    void customerNameIsFrozenAtGeneration() throws Exception {
        var kj = generert();
        Kunde k = kunder.findById(kundeId).orElseThrow();
        kunder.save(new Kunde(k.id(), k.organisasjonsnummer(), "Nytt navn kommune", k.fakturamottakerOrgnr(),
                k.avtalestatus(), k.kilde(), k.oppdatertAv(), k.oppdatertAt()));

        assertThat(csv(kj)).contains("Oslo kommune").doesNotContain("Nytt navn kommune");

        // An invoice generated before the name was frozen shows today's name, marked as such.
        jdbc.update("update faktura set kundenavn = null where kjoring_id = ?", kj.id());
        assertThat(csv(kj)).contains("Nytt navn kommune (nåværende navn)");
    }

    @Test
    @WithMockUser(username = "godkjenner", roles = {"LESER", "GODKJENNER"})
    void archivedExportSpreadsheetHasTheSameColumnsAsTheDownload() throws Exception {
        var kj = generert();
        tjeneste.godkjenn(kj.id());
        eksport.eksporter(kj.id());
        Eksportfil arkivert = eksportfiler.findByKjoringIdOrderByType(kj.id()).stream()
                .filter(f -> "CSV".equals(f.type())).findFirst().orElseThrow();
        String arkivertCsv = new String(arkiv.hent(arkivert.blobUrl()), StandardCharsets.UTF_8);

        assertThat(arkivertCsv).isEqualTo(csv(kj));
    }

    private String csv(Fakturakjoring kj) throws Exception {
        return mvc.perform(get("/kjoringer/{id}/fakturagrunnlag.csv", kj.id()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> hode(Sheet ark) {
        List<String> h = new ArrayList<>();
        ark.getRow(0).forEach(c -> h.add(c.getStringCellValue()));
        return h;
    }
}
