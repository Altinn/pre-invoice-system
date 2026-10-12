package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.UsageImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Regression for the BF1 T08 correction flow (specs/002 research R1): generate → forkast → re-import →
 * generate. Re-import used to delete the period's bruksdata, which the discarded run's lines still
 * referenced, and failed on {@code fakturalinje_bruksdata_id_fkey}.
 */
class ReimportEtterForkastIT extends BillingFixture {

    private static final String HEADER = "periode,organisasjonsnummer,produktkode,type,antall,belop\n";

    @Autowired UsageImportService usage;
    @Autowired FakturalinjeRepository linjer;
    @Autowired FakturaRepository fakturaer;

    @Test
    void reimportAfterForkastKeepsTheDiscardedRunTraceable() throws IOException {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "0.85");
        Long k = kunde("958935420", "Oslo kommune", "AKTIV", null).id();
        regel(k, "KN-1001", null, null, null);

        BruksdataImport forste = usage.importer(usage.forhaandsvis(JAN, "a.csv",
                inn(HEADER + "2027-01-01,958935420,melding,BRUKSVOLUM,1000,\n")));
        Fakturakjoring forkastet = tjeneste.generer(JAN);
        tjeneste.forkast(forkastet.id());

        // The correction: fixed usage in the source, imported again for the same period.
        BruksdataImport andre = usage.importer(usage.forhaandsvis(JAN, "b.csv",
                inn(HEADER + "2027-01-01,958935420,melding,BRUKSVOLUM,1200,\n")));
        Fakturakjoring ny = tjeneste.generer(JAN);

        assertThat(ny.status()).isEqualTo("GENERERT");
        assertThat(importer.findById(forste.id()).orElseThrow().status()).isEqualTo("AVVIST");
        assertThat(tjeneste.hent(forkastet.id()).bruksdataImportId()).isEqualTo(forste.id());
        assertThat(ny.bruksdataImportId()).isEqualTo(andre.id());

        // The new run is built from the corrected rows only.
        assertThat(antall(ny)).containsExactly(new BigDecimal("1200.00"));
        // The discarded run's lines still resolve to the rows they were built from.
        assertThat(antall(forkastet)).containsExactly(new BigDecimal("1000.00"));
        List<Fakturalinje> gamle = linjerFor(forkastet);
        assertThat(gamle).allSatisfy(l ->
                assertThat(bruksdata.findById(l.bruksdataId()).orElseThrow().importId()).isEqualTo(forste.id()));
    }

    private List<BigDecimal> antall(Fakturakjoring kj) {
        return linjerFor(kj).stream().map(l -> bruksdata.findById(l.bruksdataId()).orElseThrow().antall()).toList();
    }

    private List<Fakturalinje> linjerFor(Fakturakjoring kj) {
        return fakturaer.findByKjoringIdOrderByOrdreNr(kj.id()).stream()
                .flatMap(f -> linjer.findByFakturaIdOrderById(f.id()).stream()).toList();
    }

    private static InputStream inn(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
