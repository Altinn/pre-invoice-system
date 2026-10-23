package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.registry.KundeReferanseRepository;
import no.digdir.forsystem.registry.KundeRepository;
import no.digdir.forsystem.registry.KundenummerRegelRepository;
import no.digdir.forsystem.registry.ProduktKildenavnService;
import no.digdir.forsystem.registry.ProduktRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Uploading the datavarehus's own CSV export while the API is unreachable (OQ-19): same pipeline as a
 * fetch, product names mapped under Kildenavn, and missing customers created from the file. The same
 * upload takes forsystem's own CSV format; the header decides which.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "forvalter", roles = {"LESER", "FORVALTER"})
class DwhFilImportIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProduktKildenavnService kildenavn;
    @Autowired ProduktRepository produkter;
    @Autowired KundeRepository kunder;
    @Autowired KundenummerRegelRepository regler;
    @Autowired KundeReferanseRepository referanser;
    @Autowired BruksdataImportRepository importer;
    @Autowired BruksdataRepository bruksdata;
    @Autowired FileArchive arkiv;

    private MockMultipartFile eksport() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/dwh/eksport-2026-09.csv")) {
            return new MockMultipartFile("fil", "public.mv_altinn_usage_monthly", "application/octet-stream",
                    in.readAllBytes());
        }
    }

    private void kobleProdukter() {
        kildenavn.opprett("Autorisasjon", produkter.findByKode("autorisasjon").orElseThrow().id(), "BRUKSVOLUM");
        kildenavn.opprett("Altinn studio", produkter.findByKode("studio").orElseThrow().id(), "BRUKSVOLUM");
        kildenavn.opprett("Varsling e-post", produkter.findByKode("varsling").orElseThrow().id(), "BRUKSVOLUM");
    }

    @Test
    void uploadCreateCustomersAndConfirm() throws Exception {
        kobleProdukter();
        MockHttpSession session = new MockHttpSession();

        // Upload: the preview shows the monthly sums and that three organisations are not customers yet.
        mvc.perform(multipart("/bruksdata/last-opp").file(eksport()).param("periode", "2026-09")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("3 organisasjoner i fila finnes ikke i kunderegisteret")))
                .andExpect(content().string(Matchers.containsString("public.mv_altinn_usage_monthly")));

        // Create them from the file: name, kundenummer rule and reference as the datavarehus carries them.
        mvc.perform(post("/bruksdata/last-opp/opprett-kunder").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Opprettet 3 kunder fra fila")));
        var en = kunder.findByOrganisasjonsnummer("100000001").orElseThrow();
        assertThat(en.virksomhetsnavn()).isEqualTo("testetat en");
        assertThat(en.avtalestatus()).isEqualTo("AKTIV");
        assertThat(regler.findByKundeId(en.id())).singleElement()
                .satisfies(r -> assertThat(r.kundenummer()).isEqualTo("9001"));
        var to = kunder.findByOrganisasjonsnummer("100000002").orElseThrow();
        assertThat(referanser.findByKundeId(to.id())).singleElement()
                .satisfies(r -> assertThat(r.fakturareferanse()).isEqualTo("Navn, med komma"));
        var tre = kunder.findByOrganisasjonsnummer("100000003").orElseThrow();
        assertThat(referanser.findByKundeId(tre.id())).isEmpty();   // no reference in the file

        // Confirm: stored as a DWH import with the uploaded file archived as its raw payload.
        mvc.perform(post("/bruksdata/bekreft").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var imp = importer.findAll().stream().filter(i -> "VALIDERT".equals(i.status())).findFirst().orElseThrow();
        assertThat(imp.kilde()).isEqualTo("DWH");
        assertThat(imp.filnavn()).isEqualTo("public.mv_altinn_usage_monthly");
        assertThat(imp.raadataUrl()).endsWith(".csv");
        assertThat(new String(arkiv.hent(imp.raadataUrl()))).startsWith("\"maaned\"");
        assertThat(bruksdata.countByImportId(imp.id())).isEqualTo(4);
    }

    @Test
    void unmappedProductNamesAreRejectedUntilMappedUnderKildenavn() throws Exception {
        mvc.perform(multipart("/bruksdata/last-opp").file(eksport()).param("periode", "2026-09").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ukjent produktnavn i datavarehus: Autorisasjon")))
                .andExpect(content().string(Matchers.containsString("Import er ikke mulig")));
    }

    @Test
    void wrongPeriodIsRejected() throws Exception {
        kobleProdukter();
        mvc.perform(multipart("/bruksdata/last-opp").file(eksport()).param("periode", "2026-10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("feil periode")));
    }

    @Test
    void renamedLayoutWithTransactionMonthAndAzureCostImports() throws Exception {
        kildenavn.opprett("Autorisasjon", produkter.findByKode("autorisasjon").orElseThrow().id(), "BRUKSVOLUM");
        kildenavn.opprett("altinn-studio-applikasjonsinfrastruktur",
                produkter.findByKode("appinfra").orElseThrow().id(), "AZURE_KOSTNAD");
        MockMultipartFile august;
        try (InputStream in = getClass().getResourceAsStream("/dwh/eksport-2026-08-transaction-month.csv")) {
            august = new MockMultipartFile("fil", "csv_test_aug", "text/csv", in.readAllBytes());
        }
        MockHttpSession session = new MockHttpSession();

        mvc.perform(multipart("/bruksdata/last-opp").file(august).param("periode", "2026-08")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Eksport fra datavarehuset")))
                .andExpect(content().string(Matchers.containsString("Bekreft import")));
        mvc.perform(post("/bruksdata/bekreft").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var imp = importer.findAll().stream().filter(i -> "VALIDERT".equals(i.status())).findFirst().orElseThrow();
        // Costs with 18 decimals are stored to the øre.
        assertThat(bruksdata.findByImportId(imp.id())).extracting(Bruksdata::belop)
                .filteredOn(b -> b != null)
                .extracting(java.math.BigDecimal::toPlainString)
                .containsExactlyInAnyOrder("1234.57", "99.99");
    }

    @Test
    void forsystemsOwnFormatGoesThroughTheSameUpload() throws Exception {
        var egen = new MockMultipartFile("fil", "bruk-2026-09.csv", "text/csv",
                "periode,organisasjonsnummer,produktkode,type,antall,belop\n2026-09-01,100000001,melding,BRUKSVOLUM,12,\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/bruksdata/last-opp").file(egen).param("periode", "2026-09").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Forsystemets CSV-format")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("finnes ikke i kunderegisteret"))));
    }

    @Test
    void fileResavedByExcelIsExplained() throws Exception {
        var semikolon = new MockMultipartFile("fil", "eksport.csv", "text/csv",
                "maaned;organisation_number;product_name\n2026-09-01;100000001;Autorisasjon\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/bruksdata/last-opp").file(semikolon).param("periode", "2026-09").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("delt med semikolon")));
    }
}
