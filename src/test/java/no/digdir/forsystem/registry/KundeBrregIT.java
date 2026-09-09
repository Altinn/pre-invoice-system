package no.digdir.forsystem.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.BrregOppslag.BrregSvar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** K-16: customer creation validates the organisasjonsnummer against BRREG, with a name warning. */
class KundeBrregIT extends IntegrationTest {

    @Autowired KundeService service;
    @MockitoBean BrregOppslag brreg;

    @Test
    void rejectsWhenOrgnrNotInBrreg() {
        given(brreg.slaaOpp(anyString())).willReturn(BrregSvar.IKKE_FUNNET);
        assertThatThrownBy(() -> service.opprett("999000001", "Finnes ikke", null, Avtalestatus.AKTIV))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("BRREG");
    }

    @Test
    void acceptsWhenOrgnrFoundInBrreg() {
        given(brreg.slaaOpp(anyString())).willReturn(BrregSvar.funnet("Oslo kommune"));
        var kunde = service.opprett("958935420", "Oslo kommune", null, Avtalestatus.AKTIV);
        assertThat(kunde.id()).isNotNull();
    }

    @Test
    void allowsWhenBrregUnavailable() {
        given(brreg.slaaOpp(anyString())).willReturn(BrregSvar.UTILGJENGELIG);
        var kunde = service.opprett("912345678", "Uten BRREG-sjekk", null, Avtalestatus.AKTIV);
        assertThat(kunde.id()).isNotNull();
    }

    @Test
    void warnsOnNameMismatchButDoesNotBlock() {
        given(brreg.slaaOpp(anyString())).willReturn(BrregSvar.funnet("OSLO KOMMUNE"));
        // Creation still succeeds — the name is advisory, not blocking.
        service.opprett("958935420", "Oslo kommmune AS", null, Avtalestatus.AKTIV);
        // The mismatch is reported as the official BRREG name.
        assertThat(service.brregNavnAdvarsel("958935420", "Oslo kommmune AS")).contains("OSLO KOMMUNE");
        // Matching names (case/space-insensitive) give no warning.
        assertThat(service.brregNavnAdvarsel("958935420", "oslo kommune")).isEmpty();
    }
}
