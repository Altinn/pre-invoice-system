package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Export of an approved run: artifacts archived + eksportfil rows, LG04 invariants, PDF naming. */
class EksportIT extends BillingFixture {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");

    @Autowired EksportService eksport;
    @Autowired FileArchive arkiv;

    /** One customer, one kundenummer, two products → one invoice with two lines (multi-line). */
    private Fakturakjoring approvedMultiLineRun() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "2.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "3", null);      // 3.00
        bruk(imp, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "10", null);  // 20.00
        var kj = tjeneste.generer(JAN);
        tjeneste.godkjenn(kj.id());
        return kj;
    }

    @Test
    void exportProducesAllFourArtifactsAndSetsEksportert() {
        var kj = approvedMultiLineRun();
        eksport.eksporter(kj.id());

        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("EKSPORTERT");
        var filer = tjeneste.eksportfiler(kj.id());
        assertThat(filer).hasSize(4)
                .extracting(Eksportfil::type).containsExactlyInAnyOrder("LG04", "PDF", "CSV", "XLSX");
        assertThat(filer).allSatisfy(f -> assertThat(f.sha256()).isNotBlank());
    }

    @Test
    void lg04LineCountIsThreeTimesOrdersAndAmountsMatchTotals() {
        var kj = approvedMultiLineRun();
        eksport.eksporter(kj.id());
        Eksportfil lg04 = tjeneste.eksportfiler(kj.id()).stream()
                .filter(f -> "LG04".equals(f.type())).findFirst().orElseThrow();

        String tekst = new String(arkiv.hent(lg04.blobUrl()), WIN_1252);
        List<String> linjer = Arrays.stream(tekst.split("\n", -1)).filter(l -> !l.isEmpty()).toList();

        // Default one-order-per-line: 2 invoice lines → 2 orders → 6 LG04 lines.
        assertThat(linjer).hasSize(6);
        assertThat(linjer).allSatisfy(l -> assertThat(l).hasSize(4324));

        // Control: sum of amount-line øre equals the run's invoice total × 100.
        long oreSum = 0;
        for (int i = 1; i < linjer.size(); i += 3) { // line 2 of each order
            oreSum += Long.parseLong(linjer.get(i).substring(212, 232).trim());
        }
        long forventet = tjeneste.fakturaer(kj.id()).stream()
                .map(Faktura::sumBelop).mapToLong(b -> b.movePointRight(2).longValueExact()).sum();
        assertThat(oreSum).isEqualTo(forventet).isEqualTo(2300L); // 3.00 + 20.00 = 23.00 → 2300 øre
    }

    @Test
    void pdfZipHasOnePdfPerInvoiceNamedByKundenummerAndUuid() throws IOException {
        var kj = approvedMultiLineRun();
        eksport.eksporter(kj.id());
        Eksportfil pdf = tjeneste.eksportfiler(kj.id()).stream()
                .filter(f -> "PDF".equals(f.type())).findFirst().orElseThrow();
        var faktura = tjeneste.fakturaer(kj.id()).get(0);

        List<String> navn = new ArrayList<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(arkiv.hent(pdf.blobUrl())))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                navn.add(e.getName());
            }
        }
        assertThat(navn).containsExactly(faktura.kundenummer() + "-" + faktura.fakturaUuid() + ".pdf");
    }

    @Test
    void exportOnlyAllowedFromGodkjent() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        Long k = kunde("100000001", "Kunde", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN); // GENERERT, not approved
        assertThatThrownBy(() -> eksport.eksporter(kj.id()))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("godkjent");
    }
}
