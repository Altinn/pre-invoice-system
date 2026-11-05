package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.Kilde;
import no.digdir.forsystem.usage.Raadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Acknowledging control findings (specs/013, Økonomi's kickoff row 34): a FORVALTER or GODKJENNER ticks
 * «Funn akseptert» on a warning, every change is in the run's history, accepted warnings stop counting, and
 * approval waits until every warning is accepted. Blocking findings can never be accepted.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "okonomi", roles = {"LESER", "FORVALTER", "GODKJENNER"})
class KontrollfunnAkseptIT extends BillingFixture {

    private static final String UTEN_REF = "MANGLER_FAKTURAREFERANSE";

    @Autowired HendelsesloggRepository hendelser;
    @Autowired KontrollfunnRepository funnRepo;
    @Autowired BruksdataKandidatService kandidater;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    /** Two invoiced customers without fakturareferanse: two MANGLER_FAKTURAREFERANSE warnings, nothing blocking. */
    private Fakturakjoring kjoringMedToAdvarsler() {
        settKontoAlle();
        Long v = aktivVersjon(JAN.førsteDag(), null).id();
        pris(v, "melding", "2.0000");
        Long imp = nyImport(JAN);
        for (String orgnr : List.of("100000001", "100000002")) {
            Long k = kunde(orgnr, "Kunde " + orgnr, "AKTIV", null).id();
            regel(k, "KN" + orgnr.substring(8), null, null, null);
            bruk(imp, JAN, orgnr, "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        }
        return tjeneste.generer(JAN);
    }

    private List<Kontrollfunn> advarsler(Long kjoringId) {
        return tjeneste.funn(kjoringId).stream().filter(f -> UTEN_REF.equals(f.kode())).toList();
    }

    private List<Hendelseslogg> logg(Long kjoringId, String handling) {
        return hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc("FAKTURAKJORING", kjoringId).stream()
                .filter(h -> handling.equals(h.handling())).toList();
    }

    private String side(String url, Object... args) throws Exception {
        return mvc.perform(get(url, args)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---- Service ------------------------------------------------------------------------------

    @Test
    void acceptingOneWarningRecordsWhoAndWhenAndLogsIt() {
        var kj = kjoringMedToAdvarsler();
        Kontrollfunn f = advarsler(kj.id()).getFirst();

        tjeneste.settAksept(kj.id(), f.id(), true);

        Kontrollfunn lagret = funnRepo.findById(f.id()).orElseThrow();
        assertThat(lagret.akseptertAv()).isEqualTo("okonomi");
        assertThat(lagret.akseptertAt()).isNotNull();
        assertThat(advarsler(kj.id())).filteredOn(Kontrollfunn::erAkseptert).hasSize(1);
        assertThat(tjeneste.antallAapneAdvarsler(kj.id())).isEqualTo(1);
        List<Hendelseslogg> rader = logg(kj.id(), "AKSEPTERTE_FUNN");
        assertThat(rader).singleElement().satisfies(h -> {
            assertThat(h.bruker()).isEqualTo("okonomi");
            assertThat(h.detaljer().json()).contains("\"kode\": \"" + UTEN_REF + "\"", "\"antall\": 1",
                    "\"funnIder\": [" + f.id() + "]", "\"periode\": \"2027-01-01\"");
        });
    }

    @Test
    void unacceptingRemovesTheMarkAndLogsItAndANoOpLogsNothing() {
        var kj = kjoringMedToAdvarsler();
        Kontrollfunn f = advarsler(kj.id()).getFirst();
        tjeneste.settAksept(kj.id(), f.id(), true);
        tjeneste.settAksept(kj.id(), f.id(), true); // already accepted: no new row

        tjeneste.settAksept(kj.id(), f.id(), false);
        tjeneste.settAksept(kj.id(), f.id(), false);

        Kontrollfunn lagret = funnRepo.findById(f.id()).orElseThrow();
        assertThat(lagret.akseptertAv()).isNull();
        assertThat(lagret.akseptertAt()).isNull();
        assertThat(logg(kj.id(), "AKSEPTERTE_FUNN")).hasSize(1);
        assertThat(logg(kj.id(), "ANGRET_AKSEPT")).hasSize(1);
    }

    @Test
    void groupAcceptanceChangesEveryWarningOfTheCodeInOneLoggedAction() {
        var kj = kjoringMedToAdvarsler();
        tjeneste.settAksept(kj.id(), advarsler(kj.id()).getFirst().id(), true);

        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, true);

        assertThat(advarsler(kj.id())).hasSize(2).allMatch(Kontrollfunn::erAkseptert);
        // The second action only changed the one that was still open.
        assertThat(logg(kj.id(), "AKSEPTERTE_FUNN")).first()
                .satisfies(h -> assertThat(h.detaljer().json()).contains("\"antall\": 1"));

        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, false);
        assertThat(advarsler(kj.id())).noneMatch(Kontrollfunn::erAkseptert);
        assertThat(logg(kj.id(), "ANGRET_AKSEPT")).singleElement()
                .satisfies(h -> assertThat(h.detaljer().json()).contains("\"antall\": 2"));
    }

    @Test
    void blockingFindingsCannotBeAcceptedByServiceOrDatabase() {
        var kj = kjoringMedToAdvarsler();
        tjeneste.forkast(kj.id());
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "999999999", "melding", Bruksdatatype.BRUKSVOLUM, "5", null); // no customer
        var blokkert = tjeneste.generer(JAN);
        Kontrollfunn mangler = tjeneste.funn(blokkert.id()).stream()
                .filter(f -> "MANGLER_KUNDE".equals(f.kode())).findFirst().orElseThrow();

        assertThatThrownBy(() -> tjeneste.settAksept(blokkert.id(), mangler.id(), true))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Blokkerende funn kan ikke aksepteres");
        assertThatThrownBy(() -> tjeneste.settAkseptForKode(blokkert.id(), "MANGLER_KUNDE", true))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Blokkerende funn kan ikke aksepteres");
        // Even accepting every warning leaves the run blocked.
        aksepterAlleAdvarsler(blokkert.id());
        assertThatThrownBy(() -> tjeneste.godkjenn(blokkert.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("blokkerende");
        assertThatThrownBy(() -> jdbc.update(
                "update kontrollfunn set akseptert_av = 'x', akseptert_at = now() where id = ?", mangler.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvalWaitsUntilEveryWarningIsAccepted() {
        var kj = kjoringMedToAdvarsler();

        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("2 advarsler er ikke sjekket ut");
        tjeneste.settAksept(kj.id(), advarsler(kj.id()).getFirst().id(), true);
        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("1 advarsel er ikke sjekket ut");
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GENERERT");

        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, true);
        tjeneste.godkjenn(kj.id());
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GODKJENT");
    }

    @Test
    void acceptanceIsFrozenOnceTheRunLeavesGenerert() {
        var kj = kjoringMedToAdvarsler();
        godkjennEtterAksept(kj.id());
        Long funnId = advarsler(kj.id()).getFirst().id();

        assertThatThrownBy(() -> tjeneste.settAksept(kj.id(), funnId, false))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("status er GODKJENT");
        assertThatThrownBy(() -> tjeneste.settAkseptForKode(kj.id(), UTEN_REF, false))
                .isInstanceOf(Regelbrudd.class);
        assertThat(advarsler(kj.id())).allMatch(Kontrollfunn::erAkseptert);
    }

    @Test
    void aFindingOfAnotherRunIsRefused() {
        var kj = kjoringMedToAdvarsler();
        Long gammeltFunn = advarsler(kj.id()).getFirst().id();
        tjeneste.forkast(kj.id());
        var ny = tjeneste.generer(JAN);

        assertThatThrownBy(() -> tjeneste.settAksept(ny.id(), gammeltFunn, true))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Fant ikke kontrollfunn");
    }

    @Test
    void aRegeneratedRunStartsWithNothingAccepted() {
        var kj = kjoringMedToAdvarsler();
        aksepterAlleAdvarsler(kj.id());
        tjeneste.forkast(kj.id());

        var ny = tjeneste.generer(JAN);

        assertThat(advarsler(ny.id())).hasSize(2).noneMatch(Kontrollfunn::erAkseptert);
        assertThat(tjeneste.antallAapneAdvarsler(ny.id())).isEqualTo(2);
    }

    @Test
    void aCorrectionRunFollowsTheSameRules() {
        var v1 = kjoringMedToAdvarsler();
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        Long melding = produktId("melding");
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                volum("100000001", melding, "120"), volum("100000002", melding, "100")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("aksept-v2".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null), null);
        var sammenligning = kandidater.opprettHvisEksportert(fv).orElseThrow();
        BruksdataEndring endring = sammenligning.endringer().getFirst();
        var v2 = tjeneste.genererKorrigering(sammenligning.sammenligning().id(), List.of(endring.id()), "Retting");
        assertThat(advarsler(v2.id())).isNotEmpty();

        assertThatThrownBy(() -> tjeneste.godkjenn(v2.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("ikke sjekket ut");
        aksepterAlleAdvarsler(v2.id());
        tjeneste.godkjenn(v2.id());
        assertThat(tjeneste.hent(v2.id()).status()).isEqualTo("GODKJENT");
    }

    private static Bruksdata volum(String orgnr, Long produktId, String antall) {
        return new Bruksdata(null, null, JAN.førsteDag(), orgnr, produktId,
                Bruksdatatype.BRUKSVOLUM.name(), new java.math.BigDecimal(antall), null);
    }

    // ---- Web ----------------------------------------------------------------------------------

    @Test
    void forvalterAndGodkjennerMayAcceptButLeserMayNot() throws Exception {
        var kj = kjoringMedToAdvarsler();
        Long funnId = advarsler(kj.id()).getFirst().id();

        mvc.perform(post("/kjoringer/{id}/kontrollfunn/{f}/aksept", kj.id(), funnId).param("akseptert", "true")
                        .with(csrf()).with(user("leser").roles("LESER")))
                .andExpect(status().isForbidden());
        assertThat(funnRepo.findById(funnId).orElseThrow().erAkseptert()).isFalse();

        mvc.perform(post("/kjoringer/{id}/kontrollfunn/{f}/aksept", kj.id(), funnId).param("akseptert", "true")
                        .with(csrf()).with(user("fel").roles("FORVALTER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kjoringer/" + kj.id() + "/kontroller#funn-" + funnId));
        assertThat(funnRepo.findById(funnId).orElseThrow().akseptertAv()).isEqualTo("fel");

        // An unticked checkbox sends no value: that un-accepts.
        mvc.perform(post("/kjoringer/{id}/kontrollfunn/{f}/aksept", kj.id(), funnId)
                        .with(csrf()).with(user("okonomi").roles("GODKJENNER")))
                .andExpect(status().is3xxRedirection());
        assertThat(funnRepo.findById(funnId).orElseThrow().erAkseptert()).isFalse();

        mvc.perform(post("/kjoringer/{id}/kontrollfunn/aksept", kj.id()).param("kode", UTEN_REF)
                        .param("akseptert", "true").with(csrf()).with(user("okonomi").roles("GODKJENNER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kjoringer/" + kj.id() + "/kontroller#gruppe-" + UTEN_REF));
        assertThat(advarsler(kj.id())).allMatch(Kontrollfunn::erAkseptert);
    }

    @Test
    void kontrollerTabShowsTheCheckboxesAndTheBadgeCountsOnlyOpenWarnings() throws Exception {
        var kj = kjoringMedToAdvarsler();

        String html = side("/kjoringer/{id}/kontroller", kj.id());
        assertThat(html).contains("Funn akseptert (alle 2)", "data-auto-send",
                "2 advarsler er ikke sjekket ut", "data-count=\"2\"", "trenger ikke rettes, men må");

        tjeneste.settAksept(kj.id(), advarsler(kj.id()).getFirst().id(), true);
        html = side("/kjoringer/{id}/kontroller", kj.id());
        // MockMvc clears the mock user after a request, so the service call above ran as "system".
        assertThat(html).contains("1 av 2 akseptert", "Akseptert av ", "data-count=\"1\"",
                "1 advarsel er ikke sjekket ut");

        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, true);
        html = side("/kjoringer/{id}/kontroller", kj.id());
        assertThat(html).contains("Alle advarslene er gjennomgått og sjekket ut").doesNotContain("data-count=");
    }

    @Test
    void oversiktSaysWhatIsCheckedOutAndLocksApprovalUntilThen() throws Exception {
        var kj = kjoringMedToAdvarsler();

        String html = side("/kjoringer/{id}", kj.id());
        assertThat(html).contains("2 advarsler er ikke sjekket ut",
                "Godkjenning er låst til alle advarsler er sjekket ut");
        assertThat(godkjennKnapp(html)).contains("disabled");

        tjeneste.settAksept(kj.id(), advarsler(kj.id()).getFirst().id(), true);
        html = side("/kjoringer/{id}", kj.id());
        assertThat(html).contains("1 advarsel er ikke sjekket ut",
                "1 advarsel, gjennomgått og sjekket ut. Hindrer ikke godkjenning.");

        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, true);
        html = side("/kjoringer/{id}", kj.id());
        assertThat(html).contains("2 advarsler, gjennomgått og sjekket ut. Hindrer ikke godkjenning.")
                .doesNotContain("ikke sjekket ut");
        assertThat(godkjennKnapp(html)).doesNotContain("disabled");

        assertThat(side("/kjoringer")).contains("2 sjekket ut");
    }

    @Test
    void historikkShowsEachAcceptance() throws Exception {
        var kj = kjoringMedToAdvarsler();
        tjeneste.settAkseptForKode(kj.id(), UTEN_REF, true);

        assertThat(side("/kjoringer/{id}/historikk", kj.id())).contains("AKSEPTERTE_FUNN");
    }

    @Test
    void checkboxesAreDisabledOnceApproved() throws Exception {
        var kj = kjoringMedToAdvarsler();
        godkjennEtterAksept(kj.id());

        String html = side("/kjoringer/{id}/kontroller", kj.id());
        assertThat(html).contains("Akseptert av okonomi");
        String boks = html.substring(html.indexOf("data-auto-send"));
        assertThat(boks.substring(0, boks.indexOf('>'))).contains("disabled");
    }

    private static String godkjennKnapp(String html) {
        int i = html.indexOf("data-bekreft=\"bekreft-godkjenn\"");
        return html.substring(html.lastIndexOf("<button", i), html.indexOf('>', i) + 1);
    }
}
