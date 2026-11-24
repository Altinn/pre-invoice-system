package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import no.digdir.forsystem.common.Periode;
import org.junit.jupiter.api.Test;

/** Reading the flagged datavarehus export (specs/017) against the reduced fixture cut from the real file. */
class DwhCsvFlaggTest {

    private static final Periode OKTOBER = new Periode(java.time.LocalDate.of(2026, 10, 1));

    private static final String HODE = "\"organisation_number\",\"organisation_name\",\"product_name\",\"quantity\","
            + "\"amount_nok\",\"transaction_date\",\"source_table\",\"resource_id\",\"fakturareferanse\","
            + "\"kundenummer\",\"bestillingsnummer\",\"flag_inv_altinn\",\"flag_inv_dpv\",\"regel\"\n";

    private static DwhCsv.Fil les(String rader) {
        return DwhCsv.lesFil((HODE + rader).getBytes(StandardCharsets.UTF_8), OKTOBER);
    }

    @Test
    void onlyFlaggedRowsInThePeriodAreUsedAndEveryRowIsAccountedFor() throws IOException {
        var fil = DwhCsv.lesFil(FlaggFiksturer.bytes(), OKTOBER);
        var rader = FlaggFiksturer.rader();

        long forventetBrukt = rader.stream().filter(FlaggFiksturer::flagget)
                .filter(r -> r.get("transaction_date").startsWith("2026-10")).count();
        assertThat(fil.flagget()).isTrue();
        assertThat(fil.avviste()).isEmpty();
        assertThat(fil.brukte()).hasSize((int) forventetBrukt).allMatch(r -> Boolean.TRUE.equals(r.flaggAltinn()));
        assertThat(fil.rapport().totalt()).isEqualTo(rader.size());
        assertThat(fil.rapport().balanserer()).isTrue();
        assertThat(fil.rapport().dpv()).isEqualTo(rader.stream()
                .filter(r -> "1".equals(r.get("flag_inv_dpv"))
                        && r.get("transaction_date").startsWith("2026-10")).count());
    }

    @Test
    void otherMonthsAreCountedPerMonthNotRejected() throws IOException {
        var rapport = DwhCsv.lesFil(FlaggFiksturer.bytes(), OKTOBER).rapport();

        assertThat(rapport.utenforPerMaaned()).containsKeys(YearMonth.of(2026, 8), YearMonth.of(2026, 9))
                .doesNotContainKey(YearMonth.of(2026, 10));
        assertThat(rapport.utenforPerioden()).isEqualTo(rapport.utenforPerMaaned().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void placeholderOrgnrOnUnusedRowsNeverFailsTheRead() {
        var fil = les("""
                "NULL","owner","Autorisasjon",5,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,0,0,"ekskludert=ikke_aktiv_crm"
                "unknown","x","Autorisasjon",7,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,0,0,"ekskludert=ikke_aktiv_crm"
                "digitaliseringsdirektoratet","x","Melding",NULL,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,0,0,"ekskludert=ingen_positiv_mengde"
                "","x","Melding",3,NULL,"2026-10-03","t","urn:altinn:resource:eformidling-dpv-administrasjon",NULL,NULL,NULL,0,1,"inkludert=dpv_ressurs"
                """);

        assertThat(fil.brukte()).isEmpty();
        assertThat(fil.avviste()).isEmpty();
        assertThat(fil.rapport().utelatt()).isEqualTo(3);
        assertThat(fil.rapport().dpv()).isEqualTo(1);
        assertThat(fil.rapport().utelattVolumPerOrg()).containsEntry("ukjent orgnr", new BigDecimal("12"))
                .doesNotContainKey("owner");
        assertThat(fil.rapport().utelattPerArsak())
                .containsEntry("ekskludert=ikke_aktiv_crm", 2)
                .containsEntry("ekskludert=ingen_positiv_mengde", 1);
        assertThat(fil.rapport().balanserer()).isTrue();
    }

    @Test
    void flaggedRowWithBadOrgnrIsRejectedWithItsLineNumber() {
        var fil = les("""
                "12","x","Autorisasjon",5,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,1,0,"inkludert=positiv_mengde_og_aktiv_crm"
                """);

        assertThat(fil.brukte()).isEmpty();
        assertThat(fil.avviste()).singleElement().satisfies(a -> {
            assertThat(a.referanse()).isEqualTo("linje 2");
            assertThat(a.aarsak()).contains("ugyldig organisasjonsnummer").contains("12");
        });
        assertThat(fil.rapport().balanserer()).isTrue();
    }

    @Test
    void flagValueOtherThanZeroOrOneIsRejected() {
        var fil = les("""
                "123456789","x","Autorisasjon",5,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,2,0,"x"
                "123456789","x","Autorisasjon",5,NULL,"2026-10-03","t",NULL,NULL,NULL,NULL,,0,"x"
                """);

        assertThat(fil.brukte()).isEmpty();
        assertThat(fil.avviste()).extracting(a -> a.referanse() + " " + a.aarsak()).containsExactly(
                "linje 2 kolonne flag_inv_altinn: ugyldig verdi «2» (forventet 0 eller 1)",
                "linje 3 kolonne flag_inv_altinn: tom verdi (forventet 0 eller 1)");
        assertThat(fil.rapport().balanserer()).isTrue();
    }

    @Test
    void flaggedRowInAnotherMonthIsOtherMonthNeverUsed() {
        var fil = les("""
                "123456789","x","Autorisasjon",5,NULL,"2026-09-30","t",NULL,NULL,NULL,NULL,1,0,"x"
                """);

        assertThat(fil.brukte()).isEmpty();
        assertThat(fil.rapport().utenforPerioden()).isEqualTo(1);
    }

    @Test
    void resourceIdIsNormalisedAndExtraColumnsOrderDontMatter() {
        var fil = les("""
                "123456789","x","Melding",3,NULL,"2026-10-03","kilde",NULL,NULL,NULL,NULL,1,0,"r"
                """);
        assertThat(fil.brukte()).singleElement().satisfies(r -> {
            assertThat(r.kildetabell()).isEqualTo("kilde");
            assertThat(r.ressurs()).isNull();
        });
        assertThat(DwhCsv.ressurs("urn:altinn:resource:eformidling-dpv-taushetsbelagt"))
                .isEqualTo("eformidling-dpv-taushetsbelagt");
        assertThat(DwhCsv.ressurs("eformidling-dpv-taushetsbelagt")).isEqualTo("eformidling-dpv-taushetsbelagt");
    }

    @Test
    void customersComeFromUsedRowsOnly() throws IOException {
        var fil = DwhCsv.lesFil(FlaggFiksturer.bytes(), OKTOBER);

        assertThat(fil.kunder()).allSatisfy(k -> assertThat(k.organisasjonsnummer()).matches("[0-9]{9}"));
        assertThat(fil.kunder()).extracting(Kundedata::organisasjonsnummer)
                .isSubsetOf(fil.brukte().stream().map(DwhRad::organisasjonsnummer).toList());
    }

    @Test
    void olderExportWithoutFlagsIsReadAsBefore() throws IOException {
        byte[] gammel;
        try (var in = getClass().getResourceAsStream("/dwh/eksport-2026-09.csv")) {
            gammel = in.readAllBytes();
        }
        var fil = DwhCsv.lesFil(gammel, OKTOBER);

        assertThat(fil.flagget()).isFalse();
        assertThat(fil.rapport()).isNull();
        assertThat(fil.brukte()).hasSize(4).allMatch(r -> r.flaggAltinn() == null);
    }

    @Test
    void dpvFlagWithoutAltinnFlagIsReadAsLegacyWithAWarning() {
        String fil = "\"organisation_number\",\"product_name\",\"quantity\",\"amount_nok\",\"transaction_date\","
                + "\"flag_inv_dpv\"\n\"123456789\",\"Melding\",5,NULL,\"2026-10-03\",1\n";
        var res = DwhCsv.lesFil(fil.getBytes(StandardCharsets.UTF_8), OKTOBER);

        assertThat(res.flagget()).isFalse();
        assertThat(res.brukte()).hasSize(1);
        assertThat(res.advarsler()).singleElement().asString().contains("flag_inv_dpv").contains("flag_inv_altinn");
    }
}
