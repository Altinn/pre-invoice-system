package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.Kilde;
import no.digdir.forsystem.usage.Raadata;
import no.digdir.forsystem.usage.Sammenligningstatus;
import no.digdir.forsystem.export.EksportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class KorrigeringskjoringIT extends BillingFixture {

    @Autowired BruksdataKandidatService kandidater;
    @Autowired KorrigeringskjoringEndringRepository valgte;
    @Autowired EksportService eksport;
    @Autowired MockMvc mvc;

    @Test
    void selectedDifferenceBecomesOnlyLineInV2AndV1StaysImmutable() throws Exception {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(kunde, "KN1", null, "S1", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde2, "KN2", null, "S2", null);

        Fakturakjoring v1 = tjeneste.generer(JAN);
        int v1Linjer = tjeneste.grunnlagslinjer(v1.id()).size();
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        Long melding = produktId("melding");
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                volum("100000001", melding, "120"),
                volum("100000002", melding, "15")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("candidate-v2".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        OffsetDateTime.now(), null, null), null);
        var sammenligning = kandidater.opprettHvisEksportert(fv).orElseThrow();
        BruksdataEndring valgt = sammenligning.endringer().stream()
                .filter(e -> e.organisasjonsnummer().equals("100000001")).findFirst().orElseThrow();

        Fakturakjoring v2 = tjeneste.genererKorrigering(
                sammenligning.sammenligning().id(), List.of(valgt.id()), "DWH korrigerte volumet");

        assertThat(v2.erKorrigering()).isTrue();
        assertThat(v2.versjon()).isEqualTo(2);
        assertThat(v2.forrigeKjoringId()).isEqualTo(v1.id());
        assertThat(v2.korrigeringsaarsak()).isEqualTo("DWH korrigerte volumet");
        assertThat(valgte.endringIder(v2.id())).containsExactly(valgt.id());
        assertThat(tjeneste.fakturaer(v2.id())).hasSize(1);
        Fakturalinje linje = tjeneste.linjer(tjeneste.fakturaer(v2.id()).getFirst().id()).getFirst();
        assertThat(linje.antall()).isEqualByComparingTo("20.00");
        assertThat(linje.belop()).isEqualByComparingTo("40.00");
        assertThat(linje.bruksdataId()).isNull();
        assertThat(linje.bruksdataEndringId()).isEqualTo(valgt.id());
        assertThat(tjeneste.grunnlagslinjer(v1.id())).hasSize(v1Linjer);
        EksportService.Nedlasting regneark = eksport.regneark(v2.id(), "csv");
        assertThat(regneark.filnavn()).contains("2027-01-v2-GENERERT");
        assertThat(new String(regneark.innhold(), StandardCharsets.UTF_8).lines().findFirst().orElseThrow())
                .endsWith("kjoringstype,versjon,bruksdata_endring_id,endringstype,gammel_verdi,ny_verdi,differanse");

        mvc.perform(get("/bruksdata/sammenligninger/{id}", sammenligning.sammenligning().id())
                        .with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Endringer i bruksdata")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100000001")));
        mvc.perform(get("/kjoringer/{id}", v2.id()).with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Korrigering januar 2027 · V2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DWH korrigerte volumet")));
    }

    @Test
    void exportedPartialV2LeavesOnlyUnselectedDifferenceForV3() {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, "S1", null);
        regel(kunde2, "KN2", null, "S2", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);

        Fakturakjoring v1 = tjeneste.generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        Long melding = produktId("melding");
        Forhaandsvisning fv = kandidat(melding, "candidate-partial-v2", "120", "15");
        var forste = kandidater.opprettHvisEksportert(fv).orElseThrow();
        BruksdataEndring valgt = forste.endringer().stream()
                .filter(e -> e.organisasjonsnummer().equals("100000001")).findFirst().orElseThrow();
        Fakturakjoring v2 = tjeneste.genererKorrigering(
                forste.sammenligning().id(), List.of(valgt.id()), "Første rettelse");
        kjoringer.save(v2.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        var andre = kandidater.opprettHvisEksportert(fv).orElseThrow();

        assertThat(andre.sammenligning().grunnlagKjoringId()).isEqualTo(v2.id());
        assertThat(andre.endringer()).singleElement()
                .extracting(BruksdataEndring::organisasjonsnummer)
                .isEqualTo("100000002");
        Fakturakjoring v3 = tjeneste.genererKorrigering(
                andre.sammenligning().id(), List.of(andre.endringer().getFirst().id()), "Andre rettelse");
        assertThat(v3.versjon()).isEqualTo(3);
        assertThat(v3.forrigeKjoringId()).isEqualTo(v2.id());
    }

    @Test
    void discardedCorrectionReopensComparisonAndVersionNumberIsNotReused() throws Exception {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, "S1", null);
        regel(kunde2, "KN2", null, "S2", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);

        Fakturakjoring v1 = tjeneste.generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        var sammenligning = kandidater.opprettHvisEksportert(
                kandidat(produktId("melding"), "candidate-discard", "120", "10")).orElseThrow();
        Long endringId = sammenligning.endringer().getFirst().id();
        Fakturakjoring v2 = tjeneste.genererKorrigering(
                sammenligning.sammenligning().id(), List.of(endringId), "Utkast");

        tjeneste.forkast(v2.id());

        assertThat(kandidater.hent(sammenligning.sammenligning().id()).sammenligning().status())
                .isEqualTo(Sammenligningstatus.AAPEN.name());
        mvc.perform(get("/kjoringer/{id}", v2.id()).with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tilbake til sammenligningen")));
        Fakturakjoring v3 = tjeneste.genererKorrigering(
                sammenligning.sammenligning().id(), List.of(endringId), "Nytt utkast");
        assertThat(v3.versjon()).isEqualTo(3);
        assertThat(v3.forrigeKjoringId()).isEqualTo(v2.id());
    }

    private static Forhaandsvisning kandidat(
            Long produktId, String innhold, String forsteAntall, String andreAntall) {
        return new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                volum("100000001", produktId, forsteAntall),
                volum("100000002", produktId, andreAntall)),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata(innhold.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        OffsetDateTime.now(), null, null), null);
    }

    private static Bruksdata volum(String orgnr, Long produktId, String antall) {
        return new Bruksdata(null, null, JAN.førsteDag(), orgnr, produktId,
                Bruksdatatype.BRUKSVOLUM.name(), new BigDecimal(antall), null);
    }
}
