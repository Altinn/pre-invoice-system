package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Invoice texts per run (specs/009): defaults at generation, edits with audit, validation against the
 * LG04 fields, the lock from approval on, and the export writing the run's texts on every line.
 */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KjoringFakturateksterIT extends BillingFixture {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private static final String STANDARD_BUNNTEKST = "Se samarbeid.digdir.no for priser og innsyn i egen statistikk";

    @Autowired EksportService eksport;
    @Autowired FileArchive arkiv;
    @Autowired HendelsesloggRepository hendelser;
    @Autowired MockMvc mvc;

    /** Two customers, so the file has two orders whose every line must carry the texts. */
    private Fakturakjoring generertKjoring() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "2.0000");
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KN1", null, null, null);
        regel(kunde("100000002", "Kunde B", "AKTIV", null).id(), "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        bruk(imp, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "5", null);
        return tjeneste.generer(JAN);
    }

    private List<Hendelseslogg> endringer(Long kjoringId) {
        return hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc("FAKTURAKJORING", kjoringId).stream()
                .filter(h -> "ENDRET".equals(h.handling())).toList();
    }

    private List<String> lg04Linjer(Long kjoringId) {
        Eksportfil lg04 = tjeneste.eksportfiler(kjoringId).stream()
                .filter(f -> "LG04".equals(f.type())).findFirst().orElseThrow();
        String tekst = new String(arkiv.hent(lg04.blobUrl()), WIN_1252);
        return Arrays.stream(tekst.split("\n", -1)).filter(l -> !l.isEmpty()).toList();
    }

    // ---- Defaults (US1) ---------------------------------------------------------------------

    @Test
    void generationSetsOkonomisDefaults() {
        var kj = tjeneste.hent(generertKjoring().id());
        assertThat(kj.topptekst()).isEqualTo("Bruk av Altinn januar 2027");
        assertThat(kj.bunntekst()).isEqualTo(STANDARD_BUNNTEKST);
        assertThat(kj.varReferanse()).isEqualTo("Servicedesk@altinn.no");
    }

    @Test
    void exportWritesTheDefaultsOnEveryLine() {
        var kj = generertKjoring();
        tjeneste.godkjenn(kj.id());
        eksport.eksporter(kj.id());

        List<String> linjer = lg04Linjer(kj.id());
        assertThat(linjer).hasSize(8);   // two orders: 1 + 2×2 and 1 + 1×2
        assertThat(linjer).allSatisfy(l -> {
            assertThat(l.substring(2509, 2629).stripTrailing()).isEqualTo("Bruk av Altinn januar 2027");
            assertThat(l.substring(2629, 2749).stripTrailing()).isEqualTo(STANDARD_BUNNTEKST);
        });
        // Vår referanse on each order's header line, in both responsible fields.
        assertThat(linjer).filteredOn(l -> l.charAt(2493) == '0').hasSize(2).allSatisfy(l -> {
            assertThat(l.substring(3264, 3289)).isEqualTo(String.format("%-25s", "Servicedesk@altinn.no"));
            assertThat(l.substring(3289, 3314)).isEqualTo(String.format("%-25s", "Servicedesk@altinn.no"));
        });
    }

    // ---- Edits (US2) ------------------------------------------------------------------------

    @Test
    void anEditIsStoredExportedAndAuditedFieldByField() {
        var kj = generertKjoring();
        tjeneste.endreTekster(kj.id(), new Fakturatekster("  Bruk av Altinn januar 2027 ",
                "Nye priser fra mars: se samarbeid.digdir.no", "Servicedesk@altinn.no"));

        var lagret = tjeneste.hent(kj.id());
        assertThat(lagret.bunntekst()).isEqualTo("Nye priser fra mars: se samarbeid.digdir.no");
        assertThat(lagret.topptekst()).isEqualTo("Bruk av Altinn januar 2027");   // trimmed, so unchanged

        List<Hendelseslogg> logg = endringer(kj.id());
        assertThat(logg).hasSize(1);
        String json = logg.getFirst().detaljer().json();
        assertThat(json).contains("\"bunntekst\"", "\"fra\"", STANDARD_BUNNTEKST,
                "Nye priser fra mars: se samarbeid.digdir.no", "\"periode\"", "2027-01-01");
        assertThat(json).doesNotContain("topptekst", "varReferanse");

        tjeneste.godkjenn(kj.id());
        eksport.eksporter(kj.id());
        assertThat(lg04Linjer(kj.id())).allSatisfy(l -> assertThat(l.substring(2629, 2749).stripTrailing())
                .isEqualTo("Nye priser fra mars: se samarbeid.digdir.no"));
    }

    @Test
    void savingWithoutChangesLogsNothing() {
        var kj = generertKjoring();
        tjeneste.endreTekster(kj.id(), Fakturatekster.standard(JAN));
        assertThat(endringer(kj.id())).isEmpty();
    }

    // ---- Validation (US3) -------------------------------------------------------------------

    @Test
    void aRefusedTextChangesNothing() {
        var kj = generertKjoring();
        var forLang = new Fakturatekster("x".repeat(121), "Ny bunntekst", "Servicedesk@altinn.no");
        assertThatThrownBy(() -> tjeneste.endreTekster(kj.id(), forLang))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Topptekst").hasMessageContaining("120");
        var forLangReferanse = new Fakturatekster("Topp", "Bunn", "x".repeat(26));
        assertThatThrownBy(() -> tjeneste.endreTekster(kj.id(), forLangReferanse))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Vår referanse").hasMessageContaining("25");

        assertThat(tjeneste.hent(kj.id()).tekster()).isEqualTo(Fakturatekster.standard(JAN));
        assertThat(endringer(kj.id())).isEmpty();
    }

    @Test
    void textsAtTheLg04LimitsAreAccepted() {
        var kj = generertKjoring();
        var maks = new Fakturatekster("æ".repeat(120), "ø".repeat(120), "å".repeat(25));
        tjeneste.endreTekster(kj.id(), maks);
        assertThat(tjeneste.hent(kj.id()).tekster()).isEqualTo(maks);
    }

    // ---- Lock (US4) -------------------------------------------------------------------------

    @Test
    void textsAreLockedOnceApproved() {
        var kj = generertKjoring();
        tjeneste.godkjenn(kj.id());
        assertThatThrownBy(() -> tjeneste.endreTekster(kj.id(), new Fakturatekster("Ny", "Ny", "Ny")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("låst etter godkjenning");
        assertThat(tjeneste.hent(kj.id()).tekster()).isEqualTo(Fakturatekster.standard(JAN));
        assertThat(endringer(kj.id())).isEmpty();
    }

    @Test
    void textsAreLockedOnExportedAndDiscardedRuns() {
        var eksportert = generertKjoring();
        tjeneste.godkjenn(eksportert.id());
        eksport.eksporter(eksportert.id());
        assertThatThrownBy(() -> tjeneste.endreTekster(eksportert.id(), new Fakturatekster("Ny", "Ny", "Ny")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("låst");

        var forkastet = kjoringer.save(tjeneste.hent(eksportert.id()).medStatus("FORKASTET", null, null));
        assertThatThrownBy(() -> tjeneste.endreTekster(forkastet.id(), new Fakturatekster("Ny", "Ny", "Ny")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("låst");
    }

    @Test
    void theDatabaseRefusesAnExportableRunWithoutTexts() {
        var kj = tjeneste.hent(generertKjoring().id());
        var utenTekster = new Fakturakjoring(kj.id(), kj.periode(), kj.status(), kj.prisversjonId(),
                kj.bruksdataImportId(), kj.generertAv(), kj.generertAt(), null, null, null, kj.type(), kj.versjon(),
                kj.forrigeKjoringId(), null, null, null, null, null);
        assertThatThrownBy(() -> kjoringer.save(utenTekster))
                .hasCauseInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fakturakjoring_tekster_satt");
    }

    // ---- Web (contracts/ui.md) --------------------------------------------------------------

    @Test
    void theRunPageShowsTheFormWithLimitsWhileGenerated() throws Exception {
        var kj = generertKjoring();
        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tekster på fakturaen")))
                .andExpect(content().string(containsString("action=\"/kjoringer/" + kj.id() + "/tekster\"")))
                .andExpect(content().string(containsString("value=\"Bruk av Altinn januar 2027\"")))
                .andExpect(content().string(containsString("maxlength=\"25\"")))
                .andExpect(content().string(containsString("maks 90 på trykt faktura")))
                .andExpect(content().string(containsString("OQ-23")));
    }

    @Test
    void theRunPageShowsTheTextsReadOnlyOnceApproved() throws Exception {
        var kj = generertKjoring();
        tjeneste.godkjenn(kj.id());
        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Låst etter godkjenning")))
                .andExpect(content().string(containsString("Servicedesk@altinn.no")))
                .andExpect(content().string(not(containsString("/tekster\""))));
    }

    @Test
    void theRunPageWarnsAboveTheSuggestedPrintedLimit() throws Exception {
        var kj = generertKjoring();
        tjeneste.endreTekster(kj.id(), new Fakturatekster("x".repeat(100), "Kort", "Servicedesk@altinn.no"));
        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(content().string(containsString("Toppteksten er 100 tegn og kan bli for lang")));
    }

    @Test
    void theFormSavesAndRedirectsToTheSection() throws Exception {
        var kj = generertKjoring();
        mvc.perform(post("/kjoringer/{id}/tekster", kj.id()).with(csrf())
                        .param("topptekst", "Bruk av Altinn januar 2027")
                        .param("bunntekst", "Ny bunntekst")
                        .param("varReferanse", "Servicedesk@altinn.no"))
                .andExpect(redirectedUrl("/kjoringer/" + kj.id() + "#tekster"))
                .andExpect(flash().attribute("teksterLagret", true));
        assertThat(tjeneste.hent(kj.id()).bunntekst()).isEqualTo("Ny bunntekst");
    }

    @Test
    void aRefusedFormComesBackWithTheMessageAndTheTypedValues() throws Exception {
        var kj = generertKjoring();
        mvc.perform(post("/kjoringer/{id}/tekster", kj.id()).with(csrf())
                        .param("topptekst", "Bruk av Altinn januar 2027")
                        .param("bunntekst", "Linje en\nlinje to")
                        .param("varReferanse", "Servicedesk@altinn.no"))
                .andExpect(redirectedUrl("/kjoringer/" + kj.id() + "#tekster"))
                .andExpect(flash().attribute("teksterFeil", containsString("Bunntekst")))
                .andExpect(flash().attribute("innsendteTekster",
                        new Fakturatekster("Bruk av Altinn januar 2027", "Linje en\nlinje to", "Servicedesk@altinn.no")));
        assertThat(tjeneste.hent(kj.id()).bunntekst()).isEqualTo(STANDARD_BUNNTEKST);
    }

    @Test
    @WithMockUser(roles = {"LESER", "GODKJENNER"})
    void onlyAForvalterMayEditTheTexts() throws Exception {
        var kj = generertKjoring();
        mvc.perform(post("/kjoringer/{id}/tekster", kj.id()).with(csrf())
                        .param("topptekst", "Ny").param("bunntekst", "Ny").param("varReferanse", "Ny"))
                .andExpect(status().isForbidden());
        assertThat(tjeneste.hent(kj.id()).tekster()).isEqualTo(Fakturatekster.standard(JAN));
    }
}
