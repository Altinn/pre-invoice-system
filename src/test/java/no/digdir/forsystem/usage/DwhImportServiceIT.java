package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.FakturakjoringRepository;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.Kunde;
import no.digdir.forsystem.registry.KundeReferanse;
import no.digdir.forsystem.registry.KundeReferanseRepository;
import no.digdir.forsystem.registry.KundeRepository;
import no.digdir.forsystem.registry.KundenummerRegel;
import no.digdir.forsystem.registry.KundenummerRegelRepository;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import no.digdir.forsystem.registry.ProduktKildenavnService;
import no.digdir.forsystem.registry.ProduktRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Datavarehus usage source end to end against PostgreSQL, with the DWH itself replaced by fixtures
 * (fake orgnrs only): fetch → preview → confirm, completeness refusals, warnings, staging and the
 * scheduler, and the web endpoints.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"forsystem.dwh.enabled=true", "forsystem.dwh.base-url=https://dwh.test"})
class DwhImportServiceIT extends IntegrationTest {

    private static final Periode JAN = Periode.av(2027, 1);

    @MockitoBean DwhUsageClient klient;

    @Autowired DwhImportService dwh;
    @Autowired UsageImportService usage;
    @Autowired ProduktKildenavnService kildenavn;
    @Autowired ProduktRepository produkter;
    @Autowired BruksdataRepository bruksdata;
    @Autowired BruksdataImportRepository importer;
    @Autowired KundeRepository kunder;
    @Autowired KundeReferanseRepository referanser;
    @Autowired KundenummerRegelRepository kundenummerRegler;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired FakturakjoringRepository kjoringer;
    @Autowired MockMvc mvc;

    @BeforeEach
    void kobleProduktnavn() {
        kildenavn.opprett("Formidling", id("formidling"), "BRUKSVOLUM");
        kildenavn.opprett("Varsling e-post", id("varsling"), "BRUKSVOLUM");
        kildenavn.opprett("Autorisasjon", id("autorisasjon"), "BRUKSVOLUM");
    }

    @Test
    void fetchPreviewAndConfirmStoresMonthlySumsWithArchivedPayload() throws Exception {
        byte[] raadata = svar("januar-ok.json");

        Forhaandsvisning fv = dwh.forhaandsvis(JAN);

        assertThat(fv.kilde()).isEqualTo(Kilde.DWH);
        assertThat(fv.avviste()).isEmpty();
        assertThat(fv.kanImporteres()).isTrue();
        // 6 daily rows → 4 monthly rows; sums equal the fixture exactly (SC-002).
        assertThat(fv.gyldige()).hasSize(4);
        assertThat(sum(fv, "123456789", "formidling")).isEqualByComparingTo("150");
        assertThat(sum(fv, "123456789", "varsling")).isEqualByComparingTo("10");
        assertThat(sum(fv, "987654321", "autorisasjon")).isEqualByComparingTo("4000");

        BruksdataImport lagret = usage.importer(fv);

        assertThat(lagret.kilde()).isEqualTo("DWH");
        assertThat(lagret.status()).isEqualTo("VALIDERT");
        assertThat(lagret.raadataSha256()).isEqualTo(sha256(raadata));
        assertThat(lagret.hentetAt()).isNotNull();
        assertThat(bruksdata.countByImportId(lagret.id())).isEqualTo(4);
        assertThat(usage.raadata(lagret.id())).isEqualTo(raadata);
    }

    @Test
    void unmappedProductNameRejectsTheImport() throws IOException {
        svar("ukjent-produkt.json");
        Forhaandsvisning fv = dwh.forhaandsvis(JAN);
        assertThat(fv.avviste()).singleElement()
                .satisfies(a -> assertThat(a.aarsak()).contains("ukjent produktnavn i datavarehus: Ukjent produkt"));
        assertThat(fv.kanImporteres()).isFalse();
        assertThatThrownBy(() -> usage.importer(fv)).isInstanceOf(Regelbrudd.class);
    }

    @Test
    void incompleteResponseIsRefused() throws IOException {
        svar("med-nextlink.json");
        assertThatThrownBy(() -> dwh.forhaandsvis(JAN))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Ufullstendig svar");
    }

    @Test
    void emptyMonthIsReportedAsNotReady() throws IOException {
        svar("tom.json");
        assertThatThrownBy(() -> dwh.forhaandsvis(JAN))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Ingen bruksdata i datavarehus for januar 2027");
    }

    @Test
    void existingRunBlocksLikeCsv() throws IOException {
        svar("januar-ok.json");
        Prisversjon v = prisversjoner.save(new Prisversjon(null, "v", LocalDate.of(2027, 1, 1), null, "UTKAST"));
        kjoringer.save(new Fakturakjoring(null, JAN.førsteDag(), "GENERERT", v.id(), "test",
                OffsetDateTime.now(), null, null, null));
        assertThat(dwh.forhaandsvis(JAN).blokkeringer()).isNotEmpty();
    }

    @Test
    void customerReferenceMismatchIsAWarningNotAReject() throws IOException {
        svar("januar-ok.json");
        Kunde k = kunder.save(new Kunde(null, "123456789", "Testetat", null, "AKTIV", "MANUELL", "test",
                OffsetDateTime.now()));
        kundenummerRegler.save(new KundenummerRegel(null, k.id(), "9999", null, null, null));
        referanser.save(new KundeReferanse(null, k.id(), null, "TESTREF1", null));

        Forhaandsvisning fv = dwh.forhaandsvis(JAN);

        assertThat(fv.kanImporteres()).isTrue();
        assertThat(fv.advarsler()).singleElement().satisfies(a -> assertThat(a)
                .contains("123456789").contains("kundenummer 1234").contains("9999"));
    }

    @Test
    void largeMonthOverMonthDeviationIsWarned() throws IOException {
        Long forrige = importer.save(BruksdataImport.csv("des.csv", JAN.forrige().førsteDag(), "VALIDERT", 1,
                "test", OffsetDateTime.now())).id();
        bruksdata.save(new Bruksdata(null, forrige, JAN.forrige().førsteDag(), "123456789", id("formidling"),
                "BRUKSVOLUM", new BigDecimal("1000"), null));
        svar("januar-ok.json");

        assertThat(dwh.forhaandsvis(JAN).advarsler())
                .anySatisfy(a -> assertThat(a).contains("formidling").contains("-85 %"));
    }

    @Test
    void stageArchivesAndWaitsWithoutRowsThenConfirmCommits() throws IOException {
        byte[] raadata = svar("januar-ok.json");

        BruksdataImport staget = dwh.stage(JAN).orElseThrow();

        assertThat(staget.status()).isEqualTo("MOTTATT");
        assertThat(staget.raadataSha256()).isEqualTo(sha256(raadata));
        assertThat(bruksdata.countByImportId(staget.id())).isZero();
        assertThat(bruksdata.findByPeriode(JAN.førsteDag())).isEmpty();

        BruksdataImport bekreftet = dwh.bekreftStaget(staget.id());

        assertThat(bekreftet.id()).isEqualTo(staget.id());
        assertThat(importer.findById(staget.id()).orElseThrow().status()).isEqualTo("VALIDERT");
        assertThat(bruksdata.countByImportId(staget.id())).isEqualTo(4);
    }

    @Test
    void stagingSamePayloadTwiceIsIdempotentAndNewPayloadReplacesOld() throws IOException {
        svar("januar-ok.json");
        BruksdataImport første = dwh.stage(JAN).orElseThrow();
        assertThat(dwh.stage(JAN).orElseThrow().id()).isEqualTo(første.id());

        svar("ukjent-produkt.json");
        BruksdataImport andre = dwh.stage(JAN).orElseThrow();

        assertThat(andre.id()).isNotEqualTo(første.id());
        assertThat(importer.findById(første.id()).orElseThrow().status()).isEqualTo("AVVIST");
    }

    @Test
    void stageDoesNothingWhenPeriodAlreadyImported() throws IOException {
        svar("januar-ok.json");
        usage.importer(dwh.forhaandsvis(JAN));
        assertThat(dwh.stage(JAN)).isEmpty();
    }

    @Test
    void schedulerStagesPreviousMonthFromItsTriggerTime() throws IOException {
        svar("januar-ok.json");
        var klokke = Clock.fixed(ZonedDateTime.of(2027, 2, 7, 6, 0, 0, 0, DwhImportScheduler.OSLO).toInstant(),
                DwhImportScheduler.OSLO);

        new DwhImportScheduler(dwh, klokke).stageForrigeMaaned();

        verify(klient).hentMaaned(JAN);
        assertThat(importer.findByPeriodeAndStatusNot(JAN.førsteDag(), "AVVIST"))
                .singleElement().satisfies(i -> assertThat(i.status()).isEqualTo("MOTTATT"));
    }

    @Test
    void schedulerSwallowsDwhFailure() {
        when(klient.hentMaaned(any())).thenThrow(new Regelbrudd("nede"));
        var klokke = Clock.fixed(ZonedDateTime.of(2027, 2, 7, 6, 0, 0, 0, DwhImportScheduler.OSLO).toInstant(),
                DwhImportScheduler.OSLO);
        new DwhImportScheduler(dwh, klokke).stageForrigeMaaned();
        assertThat(importer.findByPeriodeAndStatusNot(JAN.førsteDag(), "AVVIST")).isEmpty();
    }

    // --- web ---------------------------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "FORVALTER")
    void webFetchShowsPreviewAndStagedImportCanBeOpenedAndConfirmed() throws Exception {
        svar("januar-ok.json");
        mvc.perform(get("/bruksdata")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hent fra datavarehus")));
        mvc.perform(post("/bruksdata/hent-dwh").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bekreft import")));

        BruksdataImport staget = dwh.stage(JAN).orElseThrow();
        mvc.perform(get("/bruksdata/" + staget.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("venter på bekreftelse")));
        mvc.perform(get("/bruksdata/" + staget.id() + "/raadata")).andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
        mvc.perform(post("/bruksdata/" + staget.id() + "/bekreft").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(importer.findById(staget.id()).orElseThrow().status()).isEqualTo("VALIDERT");
    }

    @Test
    @WithMockUser(roles = "LESER")
    void leserCanViewMappingButNotChangeItOrFetch() throws Exception {
        mvc.perform(get("/produkter/kildenavn")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Varsling e-post")));
        mvc.perform(post("/produkter/kildenavn").with(csrf())
                        .param("kildenavn", "X").param("produktId", "1").param("type", "BRUKSVOLUM"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bruksdata/hent-dwh").with(csrf()).param("periode", "2027-01"))
                .andExpect(status().isForbidden());
        verify(klient, never()).hentMaaned(any());
    }

    @Test
    @WithMockUser(roles = "FORVALTER")
    void duplicateMappingIsRejected() {
        assertThatThrownBy(() -> kildenavn.opprett("Formidling", id("melding"), "BRUKSVOLUM"))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("allerede koblet");
    }

    private byte[] svar(String fixture) throws IOException {
        byte[] innhold = DwhResponsTest.fixture(fixture);
        when(klient.hentMaaned(any())).thenReturn(new DwhSvar(innhold, OffsetDateTime.now()));
        return innhold;
    }

    private Long id(String kode) {
        return produkter.findByKode(kode).orElseThrow().id();
    }

    private BigDecimal sum(Forhaandsvisning fv, String orgnr, String kode) {
        Long pid = id(kode);
        List<Bruksdata> rader = fv.gyldige().stream()
                .filter(b -> b.organisasjonsnummer().equals(orgnr) && b.produktId().equals(pid)).toList();
        assertThat(rader).hasSize(1);
        return rader.getFirst().antall();
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
