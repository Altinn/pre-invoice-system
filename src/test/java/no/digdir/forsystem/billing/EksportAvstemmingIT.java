package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.export.Lg04Avvik;
import no.digdir.forsystem.export.Lg04AvstemmingVisning;
import no.digdir.forsystem.export.Lg04Eksport;
import no.digdir.forsystem.export.lg04.Lg04Ordre;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LG04 read-back reconciliation at export (specs/002 US1, FR-004..008): a matching export is stored as
 * proof; a file that does not match the run stops the export before anything is written.
 */
@AutoConfigureMockMvc
class EksportAvstemmingIT extends BillingFixture {

    @Autowired EksportService eksport;
    @Autowired Lg04AvstemmingRepository avstemminger;
    @Autowired EksportfilRepository eksportfiler;
    @Autowired HendelsesloggRepository hendelser;
    @Autowired MockMvc mvc;
    @MockitoSpyBean Lg04Eksport lg04;

    /** One customer, two products → one invoice with two lines → two LG04 orders. 3.00 + 20.00. */
    private Fakturakjoring godkjentKjoring() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "2.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);
        bruk(imp, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        var kj = tjeneste.generer(JAN);
        tjeneste.godkjenn(kj.id());
        return kj;
    }

    /** Simulate a writer defect: the file silently loses its last order. */
    @SuppressWarnings("unchecked")
    private void filenMisterSisteOrdre() {
        doAnswer(inv -> {
            List<Lg04Ordre> ordrer = inv.getArgument(0);
            return new Lg04Eksport().bygg(ordrer.subList(0, ordrer.size() - 1));
        }).when(lg04).bygg(anyList());
    }

    @Test
    void summaryAndExpectedOrdersComeFromTheRun() {
        var kj = godkjentKjoring();
        var s = tjeneste.sammendrag(kj.id());
        assertThat(s.antallFakturaer()).isEqualTo(1);
        assertThat(s.antallLinjer()).isEqualTo(2);
        assertThat(s.sumBelop()).isEqualByComparingTo("23.00");
        assertThat(s.konsistent()).isTrue();
        // One order per invoice line while OQ-1 is open.
        assertThat(eksport.forventetAntallOrdrer(kj.id())).isEqualTo(2);
    }

    @Test
    void matchingExportStoresTheReconciliation() {
        var kj = godkjentKjoring();
        eksport.eksporter(kj.id());

        var a = avstemminger.findByKjoringId(kj.id()).orElseThrow();
        assertThat(a.antallOrdrerFil()).isEqualTo(2).isEqualTo(a.antallOrdrerKjoring());
        assertThat(a.sumOreFil()).isEqualTo(2300L).isEqualTo(a.sumOreKjoring());
        assertThat(a.antallFakturaer()).isEqualTo(1);
        Eksportfil lg04Fil = eksportfiler.findById(a.eksportfilId()).orElseThrow();
        assertThat(lg04Fil.type()).isEqualTo("LG04");
        assertThat(a.sha256()).isEqualTo(lg04Fil.sha256());

        Lg04AvstemmingVisning visning = eksport.avstemmingFor(kj.id()).orElseThrow();
        assertThat(visning.kilde()).isEqualTo(Lg04AvstemmingVisning.Kilde.LAGRET);
        assertThat(visning.stemmer()).isTrue();
        assertThat(eksport.kontrollerArkiv(kj.id()).stemmer()).isTrue();
    }

    @Test
    void runExportedBeforeReconciliationIsReconciledFromTheArchive() {
        var kj = godkjentKjoring();
        eksport.eksporter(kj.id());
        avstemminger.deleteAll();   // as if exported before V9

        Lg04AvstemmingVisning visning = eksport.avstemmingFor(kj.id()).orElseThrow();
        assertThat(visning.kilde()).isEqualTo(Lg04AvstemmingVisning.Kilde.BEREGNET_FRA_ARKIV);
        assertThat(visning.stemmer()).isTrue();
        assertThat(visning.belopFil()).isEqualByComparingTo(new BigDecimal("23.00"));
        assertThat(avstemminger.findByKjoringId(kj.id())).isEmpty();   // a view never writes
    }

    @Test
    void mismatchStopsTheExportBeforeAnythingIsWritten() {
        var kj = godkjentKjoring();
        filenMisterSisteOrdre();

        assertThatThrownBy(() -> eksport.eksporter(kj.id()))
                .isInstanceOf(Lg04Avvik.class)
                .hasMessageContaining("LG04-filen har 1 ordrer / 20,00")   // formidling sorts first
                .hasMessageContaining("fakturagrunnlaget har 2 ordrer / 23,00")
                .hasMessageContaining("kjøringen er fortsatt godkjent")
                .hasMessageContaining("kontakt forsystem-teamet");
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GODKJENT");
        assertThat(eksportfiler.findByKjoringIdOrderByType(kj.id())).isEmpty();
        assertThat(avstemminger.findByKjoringId(kj.id())).isEmpty();
    }

    @Test
    @WithMockUser(username = "godkjenner", roles = {"LESER", "GODKJENNER"})
    void stoppedExportReturnsToTheRunPageWithTheExplanationAndIsLogged() throws Exception {
        var kj = godkjentKjoring();
        filenMisterSisteOrdre();

        mvc.perform(post("/kjoringer/{id}/eksporter", kj.id()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kjoringer/" + kj.id()))
                .andExpect(flash().attribute("eksportStoppet",
                        org.hamcrest.Matchers.startsWith("Eksport stoppet: LG04-filen har 1 ordrer")));

        assertThat(hendelser.findAll()).anySatisfy(h -> {
            assertThat(h.handling()).isEqualTo("EKSPORT_STOPPET");
            assertThat(h.entitetId()).isEqualTo(kj.id());
            assertThat(h.detaljer().toString()).contains("\"antallOrdrerFil\": 1").contains("\"sumOreKjoring\": 2300");
        });
    }

    @Test
    @WithMockUser(username = "leser", roles = "LESER")
    void readerSeesSummaryEmailBlockAndReconciliation() throws Exception {
        var kj = godkjentKjoring();
        eksport.eksporter(kj.id());

        mvc.perform(get("/kjoringer/{id}", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("eksl./inkl. mva uavklart")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Til e-post til regnskap")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LG04 stemmer med fakturagrunnlaget")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("23,00")));
        mvc.perform(get("/kjoringer/{id}/lg04-kontroll", kj.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LG04 stemmer med fakturagrunnlaget")));
    }
}
