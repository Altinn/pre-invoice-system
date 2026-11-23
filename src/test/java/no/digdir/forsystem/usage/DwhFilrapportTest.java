package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import no.digdir.forsystem.common.Periode;
import org.junit.jupiter.api.Test;

/** The file report of a flagged export (specs/017 FR-007, FR-013, SC-003): counts balance and are exact. */
class DwhFilrapportTest {

    private static final Periode OKTOBER = new Periode(LocalDate.of(2026, 10, 1));
    private static final String HODE = "\"organisation_number\",\"organisation_name\",\"product_name\",\"quantity\","
            + "\"amount_nok\",\"transaction_date\",\"source_table\",\"resource_id\",\"fakturareferanse\","
            + "\"kundenummer\",\"bestillingsnummer\",\"flag_inv_altinn\",\"flag_inv_dpv\",\"regel\"\n";

    @Test
    void countsEqualAnIndependentCountOfTheFixtureAndBalance() throws IOException {
        var rapport = DwhCsv.lesFil(FlaggFiksturer.bytes(), OKTOBER).rapport();
        var rader = FlaggFiksturer.rader();

        long ioktober = rader.stream().filter(r -> r.get("transaction_date").startsWith("2026-10")).count();
        long brukt = rader.stream().filter(r -> r.get("transaction_date").startsWith("2026-10")
                && FlaggFiksturer.flagget(r)).count();
        long dpv = rader.stream().filter(r -> r.get("transaction_date").startsWith("2026-10")
                && !FlaggFiksturer.flagget(r) && "1".equals(r.get("flag_inv_dpv"))).count();

        assertThat(rapport.totalt()).isEqualTo(rader.size());
        assertThat(rapport.brukt()).isEqualTo((int) brukt);
        assertThat(rapport.dpv()).isEqualTo((int) dpv);
        assertThat(rapport.utenforPerioden()).isEqualTo((int) (rader.size() - ioktober));
        assertThat(rapport.utelatt()).isEqualTo((int) (ioktober - brukt - dpv));
        assertThat(rapport.balanserer()).isTrue();
        assertThat(rapport.utelattPerArsak().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(rapport.utelatt());
    }

    @Test
    void unknownRegelTokensArePassedThroughUnchanged() {
        String rad = "\"123456789\",\"x\",\"Melding\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,0,0,"
                + "\"ekskludert_helt_nytt_token\"\n";
        var rapport = DwhCsv.lesFil((HODE + rad).getBytes(StandardCharsets.UTF_8), OKTOBER).rapport();

        assertThat(rapport.utelatt()).isEqualTo(1);
        assertThat(rapport.utelattPerArsak()).isEqualTo(Map.of("ekskludert_helt_nytt_token", 1));
        assertThat(rapport.balanserer()).isTrue();
    }

    @Test
    void rejectedRowsAreCountedSoTheReportStillBalances() {
        String rader = "\"12\",\"x\",\"Melding\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,1,0,\"r\"\n"
                + "\"123456789\",\"x\",\"Melding\",5,NULL,\"2026-10-03\",\"t\",NULL,NULL,NULL,NULL,2,0,\"r\"\n";
        var fil = DwhCsv.lesFil((HODE + rader).getBytes(StandardCharsets.UTF_8), OKTOBER);

        assertThat(fil.rapport().avvist()).isEqualTo(2);
        assertThat(fil.rapport().balanserer()).isTrue();
    }
}
