package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.registry.KundeRepository;
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
 * Uploading the flagged datavarehus export (specs/017) through the real upload path: only the DWH's
 * own «bill on the Altinn invoice» rows of the declared month become usage, with totals checked against
 * an independent sum of the fixture.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "forvalter", roles = {"LESER", "FORVALTER"})
class DwhFlaggImportIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProduktKildenavnService kildenavn;
    @Autowired ProduktRepository produkter;
    @Autowired KundeRepository kunder;
    @Autowired BruksdataImportRepository importer;
    @Autowired BruksdataRepository bruksdata;
    @Autowired no.digdir.forsystem.common.HendelsesloggRepository hendelser;

    private void kobleProdukter() {
        koble("Autorisasjon", "autorisasjon", "BRUKSVOLUM");
        koble("Altinn studio", "studio", "BRUKSVOLUM");
        koble("Melding", "melding", "BRUKSVOLUM");
        koble("Formidling", "formidling", "BRUKSVOLUM");
        koble("Varsling e-post", "varsling", "BRUKSVOLUM");
        koble("Varsling SMS (Norge)", "varsling", "BRUKSVOLUM");
        koble("Varsling SMS (utland)", "varsling", "BRUKSVOLUM");
        koble("altinn-studio-applikasjonsinfrastruktur", "appinfra", "AZURE_KOSTNAD");
    }

    private void koble(String navn, String kode, String type) {
        kildenavn.opprett(navn, produkter.findByKode(kode).orElseThrow().id(), type);
    }

    private MockMultipartFile fil(byte[] innhold) {
        return new MockMultipartFile("fil", "Mv_altinn_usage_last_3m_flag", "text/csv", innhold);
    }

    /** Expected usage per orgnr/produktkode, summed independently from the fixture for one month. */
    private Map<String, BigDecimal> forventet(String maaned) throws Exception {
        Map<String, String> kode = Map.of("Autorisasjon", "autorisasjon", "Altinn studio", "studio",
                "Melding", "melding", "Formidling", "formidling", "Varsling e-post", "varsling",
                "Varsling SMS (Norge)", "varsling", "Varsling SMS (utland)", "varsling",
                "altinn-studio-applikasjonsinfrastruktur", "appinfra");
        Map<String, BigDecimal> sum = new HashMap<>();
        for (var r : FlaggFiksturer.rader()) {
            if (!FlaggFiksturer.flagget(r) || !r.get("transaction_date").startsWith(maaned)) {
                continue;
            }
            boolean appinfra = r.get("product_name").startsWith("altinn-studio-app");
            BigDecimal v = FlaggFiksturer.tall(r.get(appinfra ? "amount_nok" : "quantity"));
            if (!appinfra && v.signum() == 0) {
                continue; // a zero day creates no usage row
            }
            sum.merge(r.get("organisation_number") + "/" + kode.get(r.get("product_name")), v, BigDecimal::add);
        }
        return sum;
    }

    private Map<String, BigDecimal> faktisk(Long importId) {
        Map<Long, String> idTilKode = new HashMap<>();
        produkter.findAll().forEach(p -> idTilKode.put(p.id(), p.kode()));
        Map<String, BigDecimal> sum = new HashMap<>();
        for (Bruksdata b : bruksdata.findByImportId(importId)) {
            sum.put(b.organisasjonsnummer() + "/" + idTilKode.get(b.produktId()),
                    b.antall() != null ? b.antall() : b.belop());
        }
        return sum;
    }

    @Test
    void importsOnlyFlaggedRowsOfTheDeclaredMonthWithExactTotals() throws Exception {
        kobleProdukter();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(multipart("/bruksdata/last-opp").file(fil(FlaggFiksturer.bytes())).param("periode", "2026-10")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Bekreft import")));
        mvc.perform(post("/bruksdata/bekreft").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var imp = importer.findAll().stream().filter(i -> "VALIDERT".equals(i.status())).findFirst().orElseThrow();
        Map<String, BigDecimal> forventet = forventet("2026-10");
        Map<String, BigDecimal> faktisk = faktisk(imp.id());

        assertThat(forventet).isNotEmpty();
        assertThat(faktisk.keySet()).isEqualTo(forventet.keySet());
        forventet.forEach((k, v) -> assertThat(faktisk.get(k)).as(k).isEqualByComparingTo(v.setScale(
                faktisk.get(k).scale() > 0 ? 2 : 0, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void septemberAndAugustImportFromTheSameFile() throws Exception {
        kobleProdukter();
        for (String maaned : new String[] {"2026-09", "2026-08"}) {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(multipart("/bruksdata/last-opp").file(fil(FlaggFiksturer.bytes())).param("periode", maaned)
                            .session(session).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("Bekreft import")));
            mvc.perform(post("/bruksdata/bekreft").session(session).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            var imp = importer.findAll().stream()
                    .filter(i -> "VALIDERT".equals(i.status()) && i.periode().toString().equals(maaned + "-01"))
                    .findFirst().orElseThrow();
            assertThat(faktisk(imp.id()).keySet()).isEqualTo(forventet(maaned).keySet());
        }
    }

    @Test
    void customersAreOfferedFromUsedRowsOnly() throws Exception {
        kobleProdukter();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(multipart("/bruksdata/last-opp").file(fil(FlaggFiksturer.bytes())).param("periode", "2026-10")
                        .session(session).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/bruksdata/last-opp/opprett-kunder").session(session).with(csrf()))
                .andExpect(status().isOk());

        // No customer is ever created for the placeholder orgnr/names of unused rows.
        for (String plassholder : new String[] {"unknown", "NULL", "owner", "digitaliseringsdirektoratet"}) {
            assertThat(kunder.findByOrganisasjonsnummer(plassholder)).isEmpty();
        }
        for (var r : FlaggFiksturer.rader()) {
            boolean brukt = FlaggFiksturer.flagget(r) && r.get("transaction_date").startsWith("2026-10");
            if (!brukt && r.get("organisation_number").matches("[0-9]{9}")
                    && FlaggFiksturer.rader().stream().noneMatch(o -> FlaggFiksturer.flagget(o)
                            && o.get("transaction_date").startsWith("2026-10")
                            && o.get("organisation_number").equals(r.get("organisation_number")))) {
                assertThat(kunder.findByOrganisasjonsnummer(r.get("organisation_number"))).isEmpty();
            }
        }
    }

    @Test
    void periodWithoutFlaggedRowsIsRefusedWithAnExplanation() throws Exception {
        kobleProdukter();
        mvc.perform(multipart("/bruksdata/last-opp").file(fil(FlaggFiksturer.bytes())).param("periode", "2026-12")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("Ingen rader i fila er merket for Altinn-fakturering")));
    }

    @Test
    void flaggedRowWithBadOrgnrBlocksTheImportWithItsLine() throws Exception {
        kobleProdukter();
        String fil = new String(FlaggFiksturer.bytes(), java.nio.charset.StandardCharsets.UTF_8)
                + "\"12\",\"x\",\"Autorisasjon\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,1,0,\"r\"\n";
        mvc.perform(multipart("/bruksdata/last-opp")
                        .file(fil(fil.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("periode", "2026-10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ugyldig organisasjonsnummer på rad merket")))
                .andExpect(content().string(Matchers.containsString("Import er ikke mulig")));
    }

    @Test
    void previewShowsTheFileReportAndConfirmAuditsTheCounts() throws Exception {
        kobleProdukter();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(multipart("/bruksdata/last-opp").file(fil(FlaggFiksturer.bytes())).param("periode", "2026-10")
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Innholdet i fila")))
                .andExpect(content().string(Matchers.containsString("Kontrollsum")))
                .andExpect(content().string(Matchers.containsString("Utelatt av datavarehuset")));
        mvc.perform(post("/bruksdata/bekreft").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var rapport = DwhCsv.lesFil(FlaggFiksturer.bytes(), new no.digdir.forsystem.common.Periode(
                java.time.LocalDate.of(2026, 10, 1))).rapport();
        String detaljer = hendelser.findTop200ByOrderByTidspunktDescIdDesc().stream()
                .filter(h -> "IMPORTERTE".equals(h.handling())).findFirst().orElseThrow().detaljer().json();
        assertThat(detaljer).contains("\"brukte\": " + rapport.brukt(), "\"utelatt\": " + rapport.utelatt(),
                "\"dpv\": " + rapport.dpv(), "\"utenforPerioden\": " + rapport.utenforPerioden());
    }

    @Test
    void invalidAndBlankFlagValuesAreRejectedWithLineColumnAndValue() throws Exception {
        kobleProdukter();
        String tekst = new String(FlaggFiksturer.bytes(), java.nio.charset.StandardCharsets.UTF_8)
                + "\"123456789\",\"x\",\"Autorisasjon\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,2,0,\"r\"\n"
                + "\"123456789\",\"x\",\"Autorisasjon\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,,0,\"r\"\n";
        mvc.perform(multipart("/bruksdata/last-opp")
                        .file(fil(tekst.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("periode", "2026-10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("kolonne flag_inv_altinn: ugyldig verdi «2»")))
                .andExpect(content().string(Matchers.containsString("kolonne flag_inv_altinn: tom verdi")))
                .andExpect(content().string(Matchers.containsString("Import er ikke mulig")));
    }

    @Test
    void unmappedProductNameOnAUsedRowPointsToKildenavn() throws Exception {
        kobleProdukter();
        String tekst = new String(FlaggFiksturer.bytes(), java.nio.charset.StandardCharsets.UTF_8)
                + "\"123456789\",\"x\",\"Helt nytt produkt\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,1,0,\"r\"\n";
        mvc.perform(multipart("/bruksdata/last-opp")
                        .file(fil(tekst.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("periode", "2026-10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Helt nytt produkt")))
                .andExpect(content().string(Matchers.containsString("Kildenavn")));
    }
}
