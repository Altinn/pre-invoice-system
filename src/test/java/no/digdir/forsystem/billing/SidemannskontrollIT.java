package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Four-eyes approval (BF1 T13, Økonomi 2026-10-13): whoever generated a run may not approve it. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "forsystem.godkjenning.sidemannskontroll=true")
class SidemannskontrollIT extends BillingFixture {

    @Autowired MockMvc mvc;

    @AfterEach
    void loggUt() {
        SecurityContextHolder.clearContext();
    }

    private Fakturakjoring generertAv(String bruker) {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        som(bruker);
        return tjeneste.generer(JAN);
    }

    private static void som(String bruker) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(bruker, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_FORVALTER"), new SimpleGrantedAuthority("ROLE_GODKJENNER"))));
    }

    @Test
    void theGeneratorCannotApprove() {
        var kj = generertAv("anne");

        assertThatThrownBy(() -> tjeneste.godkjenn(kj.id()))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("sidemannskontroll");
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GENERERT");
    }

    @Test
    void anotherApproverCan() {
        var kj = generertAv("anne");
        som("bjorn");

        tjeneste.godkjenn(kj.id());

        var godkjent = tjeneste.hent(kj.id());
        assertThat(godkjent.status()).isEqualTo("GODKJENT");
        assertThat(godkjent.generertAv()).isEqualTo("anne");
        assertThat(godkjent.godkjentAv()).isEqualTo("bjorn");
    }

    @Test
    void runPageTellsTheGeneratorThatSomeoneElseMustApprove() throws Exception {
        var kj = generertAv("anne");
        SecurityContextHolder.clearContext();

        mvc.perform(get("/kjoringer/{id}", kj.id()).with(user("anne").roles("LESER", "GODKJENNER")))
                .andExpect(content().string(Matchers.containsString("en annen med godkjennerrolle må godkjenne")));
        mvc.perform(get("/kjoringer/{id}", kj.id()).with(user("bjorn").roles("LESER", "GODKJENNER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("må godkjenne den (sidemannskontroll)"))));
    }
}
