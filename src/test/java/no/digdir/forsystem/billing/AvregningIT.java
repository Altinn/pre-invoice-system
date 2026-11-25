package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Avregning (specs/020): months ticked × price adjustment per product becomes a draft run. */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class AvregningIT extends BillingFixture {

    private static final Periode FEB = Periode.av(2027, 2);

    @Autowired AvregningService avregning;
    @Autowired MockMvc mvc;

    @BeforeEach
    void grunnlag() {
        settKontoAlle();
        regel(kunde("100000001", "Kunde A", "AKTIV", null).id(), "KA", null, null, null);
        regel(kunde("100000002", "Kunde B", "AKTIV", null).id(), "KB", null, null, null);
        Long jan = nyImport(JAN);
        bruk(jan, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(jan, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);
        bruk(jan, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "40", null);
        bruk(jan, JAN, "100000001", "appinfra", Bruksdatatype.AZURE_KOSTNAD, null, "900.00");
        Long feb = nyImport(FEB);
        bruk(feb, FEB, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "50", null);
        bruk(feb, FEB, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
    }

    private Map<Long, BigDecimal> justering(String melding) {
        return Map.of(produktId("melding"), new BigDecimal(melding));
    }

    @Test
    void summerBrukenIValgteManederOgGangerMedPrisjusteringen() {
        Fakturakjoring k = avregning.generer(2027, List.of(JAN, FEB), justering("0.2500"));

        assertThat(k.type()).isEqualTo("AVREGNING");
        assertThat(k.status()).isEqualTo("GENERERT");
        assertThat(k.periode()).isEqualTo(Periode.av(2027, 1).førsteDag());
        assertThat(k.prisversjonId()).isNull();
        assertThat(k.topptekst()).isEqualTo("Avregning bruk av Altinn 2027");
        assertThat(k.periodeLabel()).isEqualTo("avregning 2027");

        List<Faktura> fakturaer = tjeneste.fakturaer(k.id());
        assertThat(fakturaer).extracting(Faktura::organisasjonsnummer).containsExactly("100000001", "100000002");
        // Kunde A: (100 + 50) × 0,25 = 37,50. Kunde B: (40 + 10) × 0,25 = 12,50.
        assertThat(fakturaer).extracting(Faktura::sumBelop)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("37.50"), new BigDecimal("12.50"));
        Fakturalinje linje = tjeneste.linjer(fakturaer.get(0).id()).getFirst();
        assertThat(linje.antall()).isEqualByComparingTo("150");
        assertThat(linje.enhetspris()).isEqualByComparingTo("0.25");
        assertThat(linje.beskrivelse()).isEqualTo("Avregning Melding 2027");
        assertThat(avregning.maanederFor(k.id())).containsExactly(JAN.førsteDag(), FEB.førsteDag());
        assertThat(avregning.justeringerFor(k.id())).containsEntry(produktId("melding"), new BigDecimal("0.2500"));
    }

    @Test
    void bareValgteManederErMed() {
        Fakturakjoring k = avregning.generer(2027, List.of(FEB), justering("1"));

        assertThat(tjeneste.fakturaer(k.id())).extracting(Faktura::sumBelop)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50.00"), new BigDecimal("10.00"));
    }

    @Test
    void negativPrisjusteringGirKreditt() {
        Fakturakjoring k = avregning.generer(2027, List.of(JAN), justering("-0.10"));

        assertThat(tjeneste.fakturaer(k.id()).getFirst().sumBelop()).isEqualByComparingTo("-10.00");
    }

    @Test
    void applikasjonsinfrastrukturAvregnesAldri() {
        Map<Long, BigDecimal> medAppinfra = Map.of(produktId("appinfra"), new BigDecimal("1.10"));

        assertThatThrownBy(() -> avregning.generer(2027, List.of(JAN), medAppinfra))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("1:1");
    }

    @Test
    void avviserUgyldigeValg() {
        assertThatThrownBy(() -> avregning.generer(2027, List.of(), justering("1")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("minst én måned");
        assertThatThrownBy(() -> avregning.generer(2027, List.of(JAN), Map.of()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("prisjustering");
        assertThatThrownBy(() -> avregning.generer(2027, List.of(JAN), justering("0")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("prisjustering");
        assertThatThrownBy(() -> avregning.generer(2027, List.of(Periode.av(2027, 3)), justering("1")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Ingen bruksdata");
        assertThatThrownBy(() -> avregning.generer(2027, List.of(Periode.av(2028, 1)), justering("1")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("2027");
        assertThatThrownBy(() -> avregning.generer(2027, List.of(JAN),
                Map.of(produktId("autorisasjon"), new BigDecimal("1"))))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("Ingen bruk");
    }

    @Test
    void enAvregningAdgangenOgForkastetKanLagesPaaNytt() {
        Fakturakjoring v1 = avregning.generer(2027, List.of(JAN), justering("1"));

        assertThatThrownBy(() -> avregning.generer(2027, List.of(JAN), justering("1")))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("allerede en avregning");

        tjeneste.forkast(v1.id());
        Fakturakjoring v2 = avregning.generer(2027, List.of(JAN), justering("1"));
        assertThat(v2.versjon()).isEqualTo(2);
    }

    @Test
    void erUavhengigAvMaanedskjoringenForJanuar() {
        Long v = aktivVersjon(JAN.førsteDag(), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "1.0000");

        Fakturakjoring avr = avregning.generer(2027, List.of(JAN, FEB), justering("0.50"));
        Fakturakjoring januar = generer(JAN);

        assertThat(januar.type()).isEqualTo("ORDINAER");
        assertThat(januar.versjon()).isEqualTo(1);
        assertThat(januar.forrigeKjoringId()).isNull();
        assertThat(avr.versjon()).isEqualTo(1);
        assertThat(avregning.avregninger()).extracting(Fakturakjoring::id).containsExactly(avr.id());
        // The settlement is not January's run: generating January again still sees only the ordinary run.
        assertThatThrownBy(() -> generer(JAN)).isInstanceOf(Regelbrudd.class).hasMessageContaining("aktiv kjøring");
    }

    @Test
    void avregningenGodkjennesSomVanlig() {
        Fakturakjoring k = avregning.generer(2027, List.of(JAN, FEB), justering("0.25"));

        godkjennEtterAksept(k.id());

        assertThat(tjeneste.hent(k.id()).status()).isEqualTo("GODKJENT");
    }

    @Test
    void fanenViserMaanederProdukterOgForklaringPaaUnntaket() throws Exception {
        mvc.perform(get("/avregning").param("aar", "2027"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avregning")))
                .andExpect(content().string(containsString("januar 2027")))
                .andExpect(content().string(containsString("Avregnes ikke")))
                .andExpect(content().string(containsString("bokført Azure-kostnad")))
                .andExpect(content().string(containsString("Avregning bruk av Altinn 2027")));
    }

    @Test
    void skjemaetGenerererEnKjoringOgViserDen() throws Exception {
        mvc.perform(post("/avregning").with(csrf()).param("aar", "2027")
                        .param("maaned", "2027-01").param("maaned", "2027-02")
                        .param("justering-" + produktId("melding"), "0,25")
                        .param("justering-" + produktId("appinfra"), ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/kjoringer/*"));

        Fakturakjoring k = avregning.avregninger().getFirst();
        assertThat(tjeneste.fakturaer(k.id())).hasSize(2);
        mvc.perform(get("/kjoringer/" + k.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("avregning 2027")));
        mvc.perform(get("/kjoringer/" + k.id() + "/kontroller")).andExpect(status().isOk());
        mvc.perform(get("/kjoringer")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"LESER"})
    void leserKanSeFanenMenIkkeGenerere() throws Exception {
        mvc.perform(get("/avregning")).andExpect(status().isOk());
        mvc.perform(post("/avregning").with(csrf()).param("aar", "2027").param("maaned", "2027-01"))
                .andExpect(status().isForbidden());
    }
}
