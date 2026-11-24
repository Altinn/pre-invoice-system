package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Bruksdata;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.Kundeavvik;
import no.digdir.forsystem.usage.Kundeavvikskategori;
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
        regel(kunde, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde2, "KN2", null, null, null);

        Fakturakjoring v1 = generer(JAN);
        int v1Linjer = tjeneste.grunnlagslinjer(v1.id()).size();
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        Long melding = produktId("melding");
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                volum("100000001", melding, "120"),
                volum("100000002", melding, "15")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("candidate-v2".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        OffsetDateTime.now(), null, null), null);
        var sammenligning = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();
        BruksdataEndring valgt = sammenligning.endringer().stream()
                .filter(e -> e.organisasjonsnummer().equals("100000001")).findFirst().orElseThrow();

        Fakturakjoring v2 = genererKorrigering(
                sammenligning.sammenligning().id(), List.of(valgt.id()), "DWH korrigerte volumet");

        assertThat(v2.erKorrigering()).isTrue();
        assertThat(v2.versjon()).isEqualTo(2);
        assertThat(v2.forrigeKjoringId()).isEqualTo(v1.id());
        assertThat(v2.korrigeringsaarsak()).isEqualTo("DWH korrigerte volumet");
        // A correction run gets the same invoice-text defaults for its period (specs/009).
        assertThat(v2.tekster()).isEqualTo(Fakturatekster.standard(JAN));
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
    void changedReferenceIsShownPerCustomerAndRefusedForADeltaCorrection() throws Exception {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        referanse(kunde1, null, "REF-V1", null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        // CRM changed the reference before the new fetch; the candidate brings it along.
        referanse(kunde1, null, "REF-NY", "BEST-NY");
        var sammenligning = kandidater.opprettHvisEksportert(
                fraDwh(kandidat(produktId("melding"), "candidate-kundedata", "120", "10"))).orElseThrow();
        Long kandidatId = sammenligning.kandidat().id();
        assertThat(jdbc.queryForList("select organisasjonsnummer from bruksdata_kunde where import_id = ? "
                + "order by organisasjonsnummer", String.class, kandidatId))
                .containsExactly("100000001", "100000002");

        Kundeavvik avvik = sammenligning.kundeavvik().stream()
                .filter(a -> a.organisasjonsnummer().equals("100000001")).findFirst().orElseThrow();
        assertThat(avvik.kategoriliste()).containsExactly(Kundeavvikskategori.ENDRET,
                Kundeavvikskategori.ENDRET_FAKTURAREFERANSE, Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER);
        assertThat(avvik.fakturertFakturareferanse()).containsExactly("REF-V1");
        // V1's line carried the fakturareferanse as bestillingsnummer (specs/012 FR-005).
        assertThat(avvik.fakturertBestillingsnummer()).containsExactly("REF-V1");
        assertThat(avvik.kandidatFakturareferanse()).isEqualTo("REF-NY");
        assertThat(sammenligning.kundeavvik()).hasSize(1);
        assertThat(sammenligning.sammenligning().uendredeKunder()).isEqualTo(1);

        Long sammenligningId = sammenligning.sammenligning().id();
        assertThatThrownBy(() -> tjeneste.genererKorrigeringForKunder(
                sammenligningId, List.of("100000001"), false, "Volum rettet"))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("kan ikke korrigeres med differanser")
                .hasMessageContaining("Kunde 1 (100000001)");
        // The same rule holds when the change ids are given directly.
        assertThatThrownBy(() -> genererKorrigering(sammenligningId,
                List.of(sammenligning.endringer().getFirst().id()), "Volum rettet"))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("OQ-6");
        // «Velg alle» leaves out what a delta cannot correct; here that is everything.
        assertThatThrownBy(() -> tjeneste.genererKorrigeringForKunder(sammenligningId, null, true, "Alt"))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("Velg minst én kunde");
        assertThat(kjoringer.findByPeriodeAndStatusOrderByVersjonDesc(JAN.førsteDag(),
                Kjoringstatus.GENERERT.name())).isEmpty();

        mvc.perform(get("/bruksdata/sammenligninger/{id}", sammenligningId).with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Endret fakturareferanse")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Fakturareferanse: «REF-V1» → «REF-NY»")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KN1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kan ikke tas med ennå")));
    }

    @Test
    void referenceOnlyChangeIsADeviationWithDifferenceZero() throws Exception {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        referanse(kunde2, null, "PO-1", "PO-1");
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        // Same usage; only the bestillingsnummer changed, plus whitespace around the reference (not a change).
        referanse(kunde2, null, " PO-1 ", "PO-2");
        var sammenligning = kandidater.opprettHvisEksportert(
                fraDwh(kandidat(produktId("melding"), "candidate-reference-only", "100", "10"))).orElseThrow();

        assertThat(sammenligning.endringer()).isEmpty();
        assertThat(sammenligning.kundeavvik()).singleElement().satisfies(a -> {
            assertThat(a.organisasjonsnummer()).isEqualTo("100000002");
            assertThat(a.kategoriliste()).containsExactly(Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER);
        });
        mvc.perform(get("/bruksdata/sammenligninger/{id}", sammenligning.sammenligning().id())
                        .with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Endret bestillingsnummer")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Ingen endringer"))));
    }

    @Test
    void tickingACustomerTakesAllItsProductsAndNothingElse() throws Exception {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        pris(prisversjon, "varsling", "1.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000001", "varsling", Bruksdatatype.BRUKSVOLUM, "50", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        Long melding = produktId("melding");
        Long varsling = produktId("varsling");
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH, List.of(
                volum("100000001", melding, "120"),
                volum("100000001", varsling, "40"),
                volum("100000002", melding, "15")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("candidate-per-customer".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null),
                null);
        var sammenligning = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();
        assertThat(sammenligning.kundeavvik()).extracting(Kundeavvik::organisasjonsnummer)
                .containsExactly("100000001", "100000002");
        Long sammenligningId = sammenligning.sammenligning().id();

        mvc.perform(get("/bruksdata/sammenligninger/{id}", sammenligningId).with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Avvik per kunde")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-expanded=\"false\"")))
                // 100000001: +20 × 2.00 − 10 × 1.00 = +30,00 kr
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+30,00")));
        mvc.perform(get("/bruksdata/sammenligninger/{id}", sammenligningId).param("kunde", "Kunde 2")
                        .with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Viser 1 av 2 kunder.")));

        mvc.perform(post("/bruksdata/sammenligninger/{id}/kjoring", sammenligningId)
                        .with(user("forvalter").roles("FORVALTER")).with(csrf())
                        .param("orgnr", "100000001")
                        .param("aarsak", "DWH rettet kunde 1")
                        .param("grunnlagKjoringId", v1.id().toString()))
                .andExpect(status().is3xxRedirection());

        Fakturakjoring v2 = kjoringer.findFirstByPeriodeOrderByVersjonDesc(JAN.førsteDag()).orElseThrow();
        assertThat(v2.versjon()).isEqualTo(2);
        List<Long> kunde1Endringer = sammenligning.endringer().stream()
                .filter(e -> e.organisasjonsnummer().equals("100000001")).map(BruksdataEndring::id).toList();
        assertThat(valgte.endringIder(v2.id())).containsExactlyInAnyOrderElementsOf(kunde1Endringer);
        assertThat(tjeneste.fakturaer(v2.id())).singleElement()
                .satisfies(f -> assertThat(f.sumBelop()).isEqualByComparingTo("30.00"));
    }

    @Test
    void selectAllTicksEveryCustomerAndANameChangeDoesNotBlock() {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        navn(kunde2, "Kunde To");
        var sammenligning = kandidater.opprettHvisEksportert(
                fraDwh(kandidat(produktId("melding"), "candidate-select-all", "120", "15"))).orElseThrow();
        assertThat(sammenligning.kundeavvik()).filteredOn(a -> a.organisasjonsnummer().equals("100000002"))
                .singleElement().satisfies(a -> assertThat(a.kategoriliste())
                        .containsExactly(Kundeavvikskategori.ENDRET, Kundeavvikskategori.ENDRET_NAVN));

        Fakturakjoring v2 = tjeneste.genererKorrigeringForKunder(
                sammenligning.sammenligning().id(), null, true, "Alt rettet");

        assertThat(valgte.endringIder(v2.id())).hasSize(2);
        assertThat(tjeneste.fakturaer(v2.id())).hasSize(2);
    }

    @Test
    void customerWithOnlyANameChangeHasNothingToCorrect() {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        navn(kunde1, "Kunde En");
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH,
                List.of(volum("100000001", produktId("melding"), "100")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("candidate-name-only".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null),
                null);
        var sammenligning = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();

        assertThat(sammenligning.kundeavvik()).singleElement()
                .satisfies(a -> assertThat(a.kategoriliste()).containsExactly(Kundeavvikskategori.ENDRET_NAVN));
        assertThatThrownBy(() -> tjeneste.genererKorrigeringForKunder(
                sammenligning.sammenligning().id(), List.of("100000001"), false, "Navn"))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("ingen endring i bruk");
    }

    @Test
    void removedCustomerIsCreditedWithTheValuesItWasInvoicedWith() {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);
        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        // The new snapshot has no usage, and so no customer row, for 100000002.
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "ny-dwh.csv", Kilde.DWH,
                List.of(volum("100000001", produktId("melding"), "100")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata("candidate-removal".getBytes(StandardCharsets.UTF_8), OffsetDateTime.now(), null, null),
                null);
        var sammenligning = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();
        BruksdataEndring fjernet = sammenligning.endringer().stream().filter(
                e -> e.organisasjonsnummer().equals("100000002")).findFirst().orElseThrow();
        assertThat(fjernet.endringstype()).isEqualTo("FJERNET");

        Fakturakjoring v2 = genererKorrigering(sammenligning.sammenligning().id(),
                List.of(fjernet.id()), "Bruken var feilregistrert");

        assertThat(tjeneste.funn(v2.id())).noneMatch(f -> "MANGLER_KUNDENUMMER".equals(f.kode()));
        Faktura faktura = tjeneste.fakturaer(v2.id()).getFirst();
        assertThat(faktura.organisasjonsnummer()).isEqualTo("100000002");
        assertThat(faktura.kundenummer()).isEqualTo("KN2");
        assertThat(faktura.sumBelop()).isEqualByComparingTo("-20.00");
    }

    @Test
    void exportedPartialV2LeavesOnlyUnselectedDifferenceForV3() {
        settKontoAlle();
        Long prisversjon = aktivVersjon(JAN.førsteDag(), null).id();
        pris(prisversjon, "melding", "2.0000");
        Long kunde1 = kunde("100000001", "Kunde 1", "AKTIV", null).id();
        Long kunde2 = kunde("100000002", "Kunde 2", "AKTIV", null).id();
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);

        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        Long melding = produktId("melding");
        Forhaandsvisning fv = kandidat(melding, "candidate-partial-v2", "120", "15");
        var forste = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();
        BruksdataEndring valgt = forste.endringer().stream()
                .filter(e -> e.organisasjonsnummer().equals("100000001")).findFirst().orElseThrow();
        Fakturakjoring v2 = genererKorrigering(
                forste.sammenligning().id(), List.of(valgt.id()), "Første rettelse");
        kjoringer.save(v2.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));

        var andre = kandidater.opprettHvisEksportert(fraDwh(fv)).orElseThrow();

        assertThat(andre.sammenligning().grunnlagKjoringId()).isEqualTo(v2.id());
        assertThat(andre.endringer()).singleElement()
                .extracting(BruksdataEndring::organisasjonsnummer)
                .isEqualTo("100000002");
        Fakturakjoring v3 = genererKorrigering(
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
        regel(kunde1, "KN1", null, null, null);
        regel(kunde2, "KN2", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "100", null);
        bruk(imp, JAN, "100000002", "melding", Bruksdatatype.BRUKSVOLUM, "10", null);

        Fakturakjoring v1 = generer(JAN);
        kjoringer.save(v1.medStatus(Kjoringstatus.EKSPORTERT.name(), "test", OffsetDateTime.now()));
        var sammenligning = kandidater.opprettHvisEksportert(fraDwh(
                kandidat(produktId("melding"), "candidate-discard", "120", "10"))).orElseThrow();
        Long endringId = sammenligning.endringer().getFirst().id();
        Fakturakjoring v2 = genererKorrigering(
                sammenligning.sammenligning().id(), List.of(endringId), "Utkast");

        tjeneste.forkast(v2.id());

        assertThat(kandidater.hent(sammenligning.sammenligning().id()).sammenligning().status())
                .isEqualTo(Sammenligningstatus.AAPEN.name());
        mvc.perform(get("/kjoringer/{id}", v2.id()).with(user("leser").roles("LESER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tilbake til sammenligningen")));
        Fakturakjoring v3 = genererKorrigering(
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
