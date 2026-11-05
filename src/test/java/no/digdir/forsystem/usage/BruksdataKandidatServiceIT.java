package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.FakturakjoringRepository;
import no.digdir.forsystem.billing.Kjoringstype;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import no.digdir.forsystem.registry.ProduktRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BruksdataKandidatServiceIT extends IntegrationTest {

    private static final Periode JAN = Periode.av(2027, 1);

    @Autowired BruksdataKandidatService kandidater;
    @Autowired BruksdataImportRepository importer;
    @Autowired BruksdataRepository bruksdata;
    @Autowired BruksdataSammenligningRepository sammenligninger;
    @Autowired BruksdataEndringRepository endringer;
    @Autowired FakturakjoringRepository kjoringer;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired ProduktRepository produkter;

    @Test
    void archivedCandidateIsComparedWithoutReplacingValidatedImportAndRetryIsIdempotent() {
        Long melding = produkter.findByKode("melding").orElseThrow().id();
        Long formidling = produkter.findByKode("formidling").orElseThrow().id();
        BruksdataImport original = importer.save(BruksdataImport.csv("v1.csv", JAN.førsteDag(),
                Importstatus.VALIDERT.name(), 2, "test", OffsetDateTime.now()));
        bruksdata.saveAll(List.of(
                volum(original.id(), "100000001", melding, "100"),
                volum(original.id(), "100000002", formidling, "7")));
        Prisversjon prisversjon = prisversjoner.save(new Prisversjon(null, "Kandidat-test",
                JAN.førsteDag(), null, "AKTIV"));
        Fakturakjoring v1 = kjoringer.save(new Fakturakjoring(null, JAN.førsteDag(), "EKSPORTERT",
                prisversjon.id(), original.id(), "test", OffsetDateTime.now(), "test", OffsetDateTime.now(),
                null, Kjoringstype.ORDINAER.name(), 1, null, null, null, null, null, null));

        byte[] raw = "complete-dwh-candidate".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Forhaandsvisning fv = new Forhaandsvisning(JAN, "dwh-januar.csv", Kilde.DWH, List.of(
                volum(null, "100000001", melding, "120"),
                volum(null, "100000003", formidling, "9")),
                List.of(), List.of(), List.of("eksportert kjøring finnes"), List.of(),
                new Raadata(raw, OffsetDateTime.now(), null, null), null);

        var forste = kandidater.opprettHvisEksportert(fv).orElseThrow();
        var andre = kandidater.opprettHvisEksportert(fv).orElseThrow();

        assertThat(forste.kandidat().status()).isEqualTo(Importstatus.KANDIDAT.name());
        assertThat(forste.kandidat().harRaadata()).isTrue();
        assertThat(forste.sammenligning().grunnlagKjoringId()).isEqualTo(v1.id());
        assertThat(forste.sammenligning().uendredeRader()).isZero();
        assertThat(forste.endringer()).extracting(BruksdataEndring::endringstype)
                .containsExactly(Endringstype.ENDRET.name(), Endringstype.FJERNET.name(), Endringstype.TILAGT.name());
        assertThat(forste.endringer()).extracting(BruksdataEndring::delta)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("-7.00"), new BigDecimal("9.00"));
        assertThat(importer.findFirstByPeriodeAndStatusOrderByIdDesc(
                JAN.førsteDag(), Importstatus.VALIDERT.name()).orElseThrow().id()).isEqualTo(original.id());

        assertThat(andre.kandidat().id()).isEqualTo(forste.kandidat().id());
        assertThat(andre.sammenligning().id()).isEqualTo(forste.sammenligning().id());
        assertThat(sammenligninger.count()).isOne();
        assertThat(endringer.count()).isEqualTo(3);
    }

    private static Bruksdata volum(Long importId, String orgnr, Long produktId, String antall) {
        return new Bruksdata(null, importId, JAN.førsteDag(), orgnr, produktId,
                Bruksdatatype.BRUKSVOLUM.name(), new BigDecimal(antall), null);
    }
}
