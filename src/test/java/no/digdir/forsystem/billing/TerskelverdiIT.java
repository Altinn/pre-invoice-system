package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonService;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.Kilde;
import no.digdir.forsystem.usage.Raadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The invoice threshold (specs/011, OQ-22): a customer whose monthly total is not over the price
 * version's threshold gets no invoice, is listed and explained, and the threshold is locked with the
 * price version.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class TerskelverdiIT extends BillingFixture {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");

    @Autowired EksportService eksport;
    @Autowired FileArchive arkiv;
    @Autowired BruksdataavstemmingService avstemming;
    @Autowired BruksdataKandidatService kandidater;
    @Autowired PrisversjonService prisversjonService;
    @Autowired MockMvc mvc;

    private Prisversjon versjonMedTerskel(String terskel) {
        return prisversjoner.save(new Prisversjon(null, "V-" + System.nanoTime(), JAN.førsteDag(), null, "AKTIV",
                new BigDecimal(terskel)));
    }

    /**
     * melding costs 1,00 kr per unit. Kunde A 23,50 kr, Kunde B exactly 40,00 kr, Kunde C 40,01 kr, and
     * Kunde D 20,00 + 20,01 kr over two kundenumre (formidling has its own rule).
     */
    private Fakturakjoring kjoringMedFireKunder() {
        settKontoAlle();
        Long v = versjonMedTerskel("40.00").id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "1.0000");
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KA", null, null, null);
        regel(kunde("100000002", "Kunde B", "AKTIV", null).id(), "KB", null, null, null);
        regel(kunde("100000003", "Kunde C", "AKTIV", null).id(), "KC", null, null, null);
        Long d = kunde("100000004", "Kunde D", "AKTIV", null).id();
        regel(d, "KD1", null, null, null);
        regel(d, "KD2", "formidling", null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "23.5", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "30", null);
        bruk(imp, JAN, "100000002", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(imp, JAN, "100000003", "melding", Bruksdatatype.BRUKSVOLUM, "40.01", null);
        bruk(imp, JAN, "100000004", "melding", Bruksdatatype.BRUKSVOLUM, "20", null);
        bruk(imp, JAN, "100000004", "formidling", Bruksdatatype.BRUKSVOLUM, "20.01", null);
        return tjeneste.generer(JAN);
    }

    // ---- US1: who is invoiced ---------------------------------------------------------------

    @Test
    void onlyCustomersStrictlyOverTheThresholdAreInvoiced() {
        var kj = kjoringMedFireKunder();
        assertThat(tjeneste.fakturaer(kj.id())).extracting(Faktura::kundenummer)
                .containsExactly("KC", "KD1", "KD2");   // D's total 40,01 decides, not each invoice
        assertThat(tjeneste.underTerskel(kj.id()))
                .extracting(FakturaUnderTerskel::kundenummer, u -> u.sumBelop().toPlainString(),
                        u -> u.kundesum().toPlainString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("KA", "23.50", "23.50"),
                        org.assertj.core.groups.Tuple.tuple("KB", "40.00", "40.00"));
        assertThat(tjeneste.sammendrag(kj.id()).sumBelop()).isEqualByComparingTo("80.02");
    }

    @Test
    void oneWarningExplainsEveryLeftOutUsageRow() {
        var kj = kjoringMedFireKunder();
        List<Kontrollfunn> funn = tjeneste.funn(kj.id()).stream()
                .filter(f -> "UNDER_TERSKEL".equals(f.kode())).toList();
        assertThat(funn).singleElement().satisfies(f -> {
            assertThat(f.alvorlighet()).isEqualTo("ADVARSEL");
            assertThat(f.melding()).contains("2 kunder", "40,00 kr", "63,50 kr");
        });
        assertThat(tjeneste.underTerskel(kj.id())).allSatisfy(u -> assertThat(u.kontrollfunnId())
                .isEqualTo(funn.getFirst().id()));
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse();

        var a = avstemming.forKjoring(kj.id());
        assertThat(a.invariantBrudd()).isFalse();
        assertThat(a.ikkeFakturert()).hasSize(3)
                .allSatisfy(r -> assertThat(r.kode()).isEqualTo("UNDER_TERSKEL"))
                .extracting(Bruksdataavstemming.IkkeFakturert::organisasjonsnummer)
                .containsOnly("100000001", "100000002");
    }

    @Test
    void leftOutCustomersAreNotInTheLg04File() {
        var kj = kjoringMedFireKunder();
        godkjennEtterAksept(kj.id());
        eksport.eksporter(kj.id());
        Eksportfil lg04 = tjeneste.eksportfiler(kj.id()).stream()
                .filter(f -> "LG04".equals(f.type())).findFirst().orElseThrow();
        List<String> linjer = Arrays.stream(new String(arkiv.hent(lg04.blobUrl()), WIN_1252).split("\n"))
                .filter(l -> !l.isEmpty()).toList();
        assertThat(linjer).filteredOn(l -> l.charAt(2493) == '0')
                .extracting(l -> l.substring(233, 258).trim()).containsExactly("KC", "KD1", "KD2");
    }

    @Test
    void noThresholdWhenTheVersionHasNone() {
        settKontoAlle();
        Long v = aktivVersjon(JAN.førsteDag(), null).id();   // terskelverdi null
        pris(v, "melding", "1.0000");
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KA", null, null, null);
        bruk(nyImport(JAN), JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        var kj = tjeneste.generer(JAN);
        assertThat(tjeneste.fakturaer(kj.id())).hasSize(1);
        assertThat(tjeneste.underTerskel(kj.id())).isEmpty();
        assertThat(tjeneste.funn(kj.id())).noneMatch(f -> "UNDER_TERSKEL".equals(f.kode()));
    }

    @Test
    void correctionRunsAreExempt() {
        settKontoAlle();
        Long v = versjonMedTerskel("40.00").id();
        pris(v, "melding", "2.0000");
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KA", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        Fakturakjoring v1 = tjeneste.generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        Long melding = produktId("melding");
        var fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(new Bruksdata(null, null,
                JAN.førsteDag(), "100000001", melding, Bruksdatatype.BRUKSVOLUM.name(), new BigDecimal("110"), null)),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("terskel-korrigering".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null),
                null);
        var sammenligning = kandidater.opprettHvisEksportert(fv).orElseThrow();
        Fakturakjoring v2 = tjeneste.genererKorrigering(sammenligning.sammenligning().id(),
                List.of(sammenligning.endringer().getFirst().id()), "DWH rettet volumet");

        // The difference is 10 × 2,00 = 20,00 kr, under 40 — still invoiced in a correction run.
        assertThat(tjeneste.fakturaer(v2.id())).singleElement()
                .satisfies(f -> assertThat(f.sumBelop()).isEqualByComparingTo("20.00"));
        assertThat(tjeneste.underTerskel(v2.id())).isEmpty();
    }

    // ---- US2: the list and the downloads ----------------------------------------------------

    @Test
    void theFakturaerTabListsTheLeftOutCustomers() throws Exception {
        var kj = kjoringMedFireKunder();
        mvc.perform(get("/kjoringer/{id}/fakturaer", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Faktureres ikke (under terskelverdi)")))
                .andExpect(content().string(containsString("2 fakturaer er holdt utenfor")))
                .andExpect(content().string(containsString("Kunde A")))
                .andExpect(content().string(containsString("23,50")))
                .andExpect(content().string(containsString("/under-terskel.xlsx")))
                .andExpect(content().string(containsString("/fakturaliste.xlsx")));
    }

    @Test
    void oneLeftOutInvoiceIsSingular() throws Exception {
        settKontoAlle();
        Long v = versjonMedTerskel("40.00").id();
        pris(v, "melding", "1.0000");
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KA", null, null, null);
        regel(kunde("100000002", "Kunde B", "AKTIV", null).id(), "KB", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        var kj = tjeneste.generer(JAN);
        mvc.perform(get("/kjoringer/{id}/fakturaer", kj.id()))
                .andExpect(content().string(containsString("1 faktura er holdt utenfor")));
    }

    @Test
    void bothListsDownloadAsCsvAndXlsx() throws Exception {
        var kj = kjoringMedFireKunder();
        String csv = mvc.perform(get("/kjoringer/{id}/under-terskel.csv", kj.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("under-terskel-2027-01-GENERERT.csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.lines()).hasSize(3);
        assertThat(csv).contains("2027-01,KA,Kunde A,100000001,,1,23.50,23.50,40.00", "2027-01,KB,Kunde B,100000002,,2,40.00,40.00,40.00");

        String fakturaliste = mvc.perform(get("/kjoringer/{id}/fakturaliste.csv", kj.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(fakturaliste.lines()).hasSize(4);   // header + KC, KD1, KD2

        byte[] xlsx = mvc.perform(get("/kjoringer/{id}/under-terskel.xlsx", kj.id()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var rad = wb.getSheetAt(0).getRow(1);
            assertThat(rad.getCell(6).getNumericCellValue()).isEqualTo(23.5);   // sum_eksl_mva is a number
        }
    }

    // ---- US3: the threshold on the price version --------------------------------------------

    @Test
    void aNewPriceVersionStartsAt40AndCanBeChangedOnlyAsUtkast() {
        var utkast = prisversjonService.opprettUtkast("Prisliste 2028", LocalDate.of(2028, 1, 1), null);
        assertThat(utkast.terskelverdi()).isEqualByComparingTo("40.00");

        prisversjonService.settTerskelverdi(utkast.id(), new BigDecimal("50"));
        assertThat(prisversjonService.hent(utkast.id()).terskelverdi()).isEqualByComparingTo("50.00");
        assertThatThrownBy(() -> prisversjonService.settTerskelverdi(utkast.id(), new BigDecimal("-1")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("0 eller mer");

        var aktiv = versjonMedTerskel("40.00");
        assertThatThrownBy(() -> prisversjonService.settTerskelverdi(aktiv.id(), BigDecimal.TEN))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("bare endres i et utkast");
    }

    @Test
    void activationAndArchivingKeepTheThreshold() {
        settKontoAlle();
        var utkast = prisversjonService.opprettUtkast("Prisliste 2028", LocalDate.of(2028, 1, 1), null);
        prisversjonService.settTerskelverdi(utkast.id(), new BigDecimal("55.00"));
        produkter.findAllByOrderByKode().forEach(p ->
                prisversjonService.settPris(utkast.id(), p.id(), BigDecimal.ONE));
        prisversjonService.aktiver(utkast.id());
        assertThat(prisversjonService.hent(utkast.id()).terskelverdi()).isEqualByComparingTo("55.00");
        prisversjonService.arkiver(utkast.id());
        assertThat(prisversjonService.hent(utkast.id()).terskelverdi()).isEqualByComparingTo("55.00");
    }

    @Test
    void thePriceVersionPageEditsTheThresholdWhileUtkast() throws Exception {
        var utkast = prisversjonService.opprettUtkast("Prisliste 2028", LocalDate.of(2028, 1, 1), null);
        mvc.perform(get("/prisversjoner/{id}", utkast.id()))
                .andExpect(content().string(containsString("Lagre terskelverdi")))
                .andExpect(content().string(containsString("value=\"40.00\"")));
        mvc.perform(post("/prisversjoner/{id}/terskelverdi", utkast.id()).with(csrf()).param("terskelverdi", "45.5"))
                .andExpect(status().is3xxRedirection());
        assertThat(prisversjonService.hent(utkast.id()).terskelverdi()).isEqualByComparingTo("45.50");

        var aktiv = versjonMedTerskel("40.00");
        mvc.perform(get("/prisversjoner/{id}", aktiv.id()))
                .andExpect(content().string(containsString("40,00 kr eksl. mva per kunde per måned")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Lagre terskelverdi"))));
    }
}
