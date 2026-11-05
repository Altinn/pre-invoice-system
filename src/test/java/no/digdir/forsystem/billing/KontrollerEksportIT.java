package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.Kilde;
import no.digdir.forsystem.usage.Raadata;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Kontroller tab's lists as CSV and XLSX (specs/013, Økonomi's kickoff row 35): the findings with their
 * acceptance, the usage reconciliation per product and type, and the usage not invoiced. Every download is
 * logged on the run.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "okonomi", roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KontrollerEksportIT extends BillingFixture {

    @Autowired EksportService eksport;
    @Autowired HendelsesloggRepository hendelser;
    @Autowired MockMvc mvc;
    @Autowired BruksdataKandidatService kandidater;

    /**
     * One invoiced customer without reference (a warning) and usage for an unknown orgnr (blocking, not
     * invoiced).
     */
    private Fakturakjoring kjoring() {
        settKontoAlle();
        Long v = aktivVersjon(JAN.førsteDag(), null).id();
        pris(v, "melding", "2.0000");
        Long imp = nyImport(JAN);
        Long k = kunde("100000001", "Kunde En", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "999999999", "melding", Bruksdatatype.BRUKSVOLUM, "7", null);
        return tjeneste.generer(JAN);
    }

    private String csv(String url, Object... args) throws Exception {
        return new String(mvc.perform(get(url, args)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private XSSFWorkbook xlsx(String url, Object... args) throws Exception {
        byte[] innhold = mvc.perform(get(url, args)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(innhold));
    }

    @Test
    void kontrollfunnListHasOneRowPerFindingWithItsAcceptance() throws Exception {
        var kj = kjoring();
        Kontrollfunn advarsel = tjeneste.funn(kj.id()).stream()
                .filter(f -> "MANGLER_FAKTURAREFERANSE".equals(f.kode())).findFirst().orElseThrow();
        tjeneste.settAksept(kj.id(), advarsel.id(), true);

        List<String> linjer = csv("/kjoringer/{id}/kontrollfunn.csv", kj.id()).lines().toList();
        assertThat(linjer.getFirst()).isEqualTo("periode,alvorlighet,kode,beskrivelse,ordre,kundenummer,kundenavn,"
                + "funn_akseptert,akseptert_av,akseptert_tidspunkt");
        assertThat(linjer).hasSize(1 + tjeneste.funn(kj.id()).size());
        // Blocking first, with no acceptance column; the accepted warning names who.
        assertThat(linjer.get(1)).startsWith("2027-01,BLOKKERENDE,MANGLER_KUNDE,").endsWith(",,,");
        assertThat(linjer).anySatisfy(l -> assertThat(l)
                .startsWith("2027-01,ADVARSEL,MANGLER_FAKTURAREFERANSE,")
                .contains(",KN1,Kunde En,ja,okonomi,20").doesNotContain("nei"));

        try (var wb = xlsx("/kjoringer/{id}/kontrollfunn.xlsx", kj.id())) {
            var ark = wb.getSheet("kontrollfunn");
            assertThat(ark.getRow(0).getCell(7).getStringCellValue()).isEqualTo("funn_akseptert");
            assertThat(ark.getLastRowNum()).isEqualTo(tjeneste.funn(kj.id()).size());
        }
    }

    @Test
    void reconciliationListsMatchTheTab() throws Exception {
        var kj = kjoring();

        String avstemming = csv("/kjoringer/{id}/avstemming-bruksdata.csv", kj.id());
        assertThat(avstemming.lines().findFirst().orElseThrow()).isEqualTo("periode,produkt,produktnavn,type,"
                + "importerte_rader,importert_bruksdata,fakturerte_linjer,fakturert_bruksdata,ikke_fakturerte_rader");
        assertThat(avstemming).contains("2027-01,melding,");
        assertThat(avstemming.lines().filter(l -> l.contains(",melding,")).findFirst().orElseThrow())
                .endsWith(",BRUKSVOLUM,2,107.00,1,100.00,1");

        String ikkeFakturert = csv("/kjoringer/{id}/ikke-fakturert.csv", kj.id());
        assertThat(ikkeFakturert.lines().toList()).containsExactly(
                "periode,organisasjonsnummer,produkt,type,antall,belop_eksl_mva,kode,arsak",
                ikkeFakturert.lines().skip(1).findFirst().orElseThrow());
        assertThat(ikkeFakturert).contains("2027-01,999999999,melding,BRUKSVOLUM,7.00,,MANGLER_KUNDE,");

        try (var wb = xlsx("/kjoringer/{id}/avstemming-bruksdata.xlsx", kj.id())) {
            var rad = wb.getSheet("avstemming-bruksdata").getRow(1);
            assertThat(rad.getCell(4).getNumericCellValue()).isGreaterThanOrEqualTo(0); // numbers stay numbers
        }
        try (var wb = xlsx("/kjoringer/{id}/ikke-fakturert.xlsx", kj.id())) {
            assertThat(wb.getSheet("ikke-fakturert").getRow(1).getCell(4).getNumericCellValue()).isEqualTo(7.0);
        }
    }

    @Test
    void everyDownloadIsLoggedAndTheTabOffersThem() throws Exception {
        var kj = kjoring();
        String html = new String(mvc.perform(get("/kjoringer/{id}/kontroller", kj.id())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(html).contains("kontrollfunn.xlsx", "kontrollfunn.csv", "avstemming-bruksdata.xlsx",
                "avstemming-bruksdata.csv", "ikke-fakturert.xlsx", "ikke-fakturert.csv");

        for (String liste : List.of("kontrollfunn", "avstemming-bruksdata", "ikke-fakturert")) {
            mvc.perform(get("/kjoringer/{id}/" + liste + ".xlsx", kj.id()))
                    .andExpect(header().string("Content-Disposition",
                            containsString(liste + "-2027-01-GENERERT.xlsx")));
        }
        var nedlastinger = hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc("FAKTURAKJORING", kj.id())
                .stream().filter(h -> "LASTET_NED".equals(h.handling())).toList();
        assertThat(nedlastinger).hasSize(3);
        assertThat(nedlastinger).anySatisfy(h -> assertThat(h.detaljer().json())
                .contains("\"liste\": \"ikke-fakturert\"", "\"format\": \"XLSX\"", "\"status\": \"GENERERT\""));
    }

    @Test
    void aCorrectionRunHasNoUsageReconciliationToDownload() {
        var kj = kjoring();
        kjoringer.save(kj.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                new Bruksdata(null, null, JAN.førsteDag(), "100000001", produktId("melding"),
                        Bruksdatatype.BRUKSVOLUM.name(), new BigDecimal("120"), null)),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("eksport-v2".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null), null);
        var sammenligning = kandidater.opprettHvisEksportert(fv).orElseThrow();
        var korrigering = tjeneste.genererKorrigering(sammenligning.sammenligning().id(),
                List.of(sammenligning.endringer().getFirst().id()), "Retting");

        assertThatThrownBy(() -> eksport.liste(korrigering.id(), "avstemming-bruksdata", "csv"))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("ingen avstemming av bruksdata");
        assertThatThrownBy(() -> eksport.liste(korrigering.id(), "ikke-fakturert", "xlsx"))
                .isInstanceOf(Regelbrudd.class);
        assertThat(eksport.liste(korrigering.id(), "kontrollfunn", "csv").filnavn())
                .isEqualTo("kontrollfunn-2027-01-v2-GENERERT.csv");
    }
}
