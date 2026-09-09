package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import no.digdir.forsystem.IntegrationTest;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.FakturakjoringRepository;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.Prisversjon;
import no.digdir.forsystem.registry.PrisversjonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Usage import: golden path, every validation rule, re-import replacement, and the run block. */
class UsageImportServiceIT extends IntegrationTest {

    private static final Periode JAN = Periode.av(2027, 1);
    private static final String HEADER = "periode,organisasjonsnummer,produktkode,type,antall,belop\n";

    @Autowired UsageImportService service;
    @Autowired BruksdataRepository bruksdata;
    @Autowired BruksdataImportRepository importer;
    @Autowired PrisversjonRepository prisversjoner;
    @Autowired FakturakjoringRepository kjoringer;

    @Test
    void goldenPathImportsAndTotalsMatch() throws IOException {
        String csv = HEADER
                + "2027-01-01,123456789,melding,BRUKSVOLUM,1500,\n"
                + "2027-01-01,123456789,varsling,SMS_KOSTNAD,,842.50\n";
        Forhaandsvisning fv = service.forhaandsvis(JAN, "bruk.csv", inn(csv));

        assertThat(fv.avviste()).isEmpty();
        assertThat(fv.kanImporteres()).isTrue();
        assertThat(fv.summer()).anySatisfy(s -> {
            if (s.produktkode().equals("melding")) {
                assertThat(s.sumAntall()).isEqualByComparingTo("1500");
            }
        });

        BruksdataImport lagret = service.importer(fv);
        assertThat(lagret.status()).isEqualTo("VALIDERT");
        assertThat(lagret.antallRader()).isEqualTo(2);
        assertThat(bruksdata.countByImportId(lagret.id())).isEqualTo(2);
    }

    @Test
    void unknownProductIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-01-01,123456789,finnesikke,BRUKSVOLUM,1,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("ukjent produktkode"));
    }

    @Test
    void malformedOrgnrIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-01-01,12345,melding,BRUKSVOLUM,1,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("organisasjonsnummer"));
    }

    @Test
    void wrongPeriodIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-02-01,123456789,melding,BRUKSVOLUM,1,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("feil periode"));
    }

    @Test
    void negativeValueIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-01-01,123456789,melding,BRUKSVOLUM,-5,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("negativ"));
    }

    @Test
    void nonNumericValueIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-01-01,123456789,melding,BRUKSVOLUM,mye,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("ugyldig"));
    }

    @Test
    void invalidTypeIsRejected() throws IOException {
        var fv = service.forhaandsvis(JAN, "f.csv", inn(HEADER + "2027-01-01,123456789,melding,TULL,1,\n"));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("type"));
    }

    @Test
    void duplicateRowIsRejected() throws IOException {
        String csv = HEADER
                + "2027-01-01,123456789,melding,BRUKSVOLUM,10,\n"
                + "2027-01-01,123456789,melding,BRUKSVOLUM,20,\n";
        var fv = service.forhaandsvis(JAN, "f.csv", inn(csv));
        assertThat(fv.avviste()).singleElement().satisfies(a -> assertThat(a.aarsak()).contains("duplikat"));
    }

    @Test
    void rejectedFileCannotBeImportedAndLeavesZeroRows() throws IOException {
        // One good row, one bad → all-or-nothing means nothing persists.
        String csv = HEADER
                + "2027-01-01,123456789,melding,BRUKSVOLUM,10,\n"
                + "2027-01-01,BAD,varsling,BRUKSVOLUM,20,\n";
        Forhaandsvisning fv = service.forhaandsvis(JAN, "f.csv", inn(csv));
        assertThat(fv.kanImporteres()).isFalse();
        assertThatThrownBy(() -> service.importer(fv)).isInstanceOf(Regelbrudd.class);
        assertThat(bruksdata.count()).isZero();
    }

    @Test
    void reimportReplacesRowsAndMarksOldImportAvvist() throws IOException {
        BruksdataImport first = service.importer(
                service.forhaandsvis(JAN, "a.csv", inn(HEADER
                        + "2027-01-01,123456789,melding,BRUKSVOLUM,10,\n"
                        + "2027-01-01,123456789,varsling,BRUKSVOLUM,20,\n")));

        BruksdataImport second = service.importer(
                service.forhaandsvis(JAN, "b.csv", inn(HEADER
                        + "2027-01-01,999999999,formidling,BRUKSVOLUM,7,\n")));

        assertThat(importer.findById(first.id()).orElseThrow().status()).isEqualTo("AVVIST");
        assertThat(bruksdata.countByImportId(first.id())).isZero();
        assertThat(bruksdata.countByImportId(second.id())).isEqualTo(1);
    }

    @Test
    void importBlockedWhenNonForkastetRunExistsForPeriod() throws IOException {
        Prisversjon v = prisversjoner.save(new Prisversjon(null, "v", LocalDate.of(2027, 1, 1), null, "UTKAST"));
        kjoringer.save(new Fakturakjoring(null, JAN.førsteDag(), "GENERERT", v.id(), "test",
                OffsetDateTime.now(), null, null, null));

        Forhaandsvisning fv = service.forhaandsvis(JAN, "f.csv",
                inn(HEADER + "2027-01-01,123456789,melding,BRUKSVOLUM,10,\n"));

        assertThat(fv.blokkeringer()).isNotEmpty();
        assertThat(fv.kanImporteres()).isFalse();
        assertThatThrownBy(() -> service.importer(fv)).isInstanceOf(Regelbrudd.class);
    }

    private static InputStream inn(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
