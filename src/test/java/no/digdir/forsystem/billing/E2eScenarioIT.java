package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.usage.UsageImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Full monthly cycle end-to-end (docs/04 Phase 5, docs/05): CSV import → generate → approve →
 * export, verifying the LG04 file, the archived export files, and that each step is audited.
 */
class E2eScenarioIT extends BillingFixture {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");
    private static final String CSV = """
            periode,organisasjonsnummer,produktkode,type,antall,belop
            2027-01-01,958935420,melding,BRUKSVOLUM,1000,
            2027-01-01,958935420,varsling,SMS_KOSTNAD,,250.50
            """;

    @Autowired UsageImportService usage;
    @Autowired EksportService eksport;
    @Autowired FileArchive arkiv;
    @Autowired HendelsesloggRepository hendelser;

    @Test
    void importGenerateApproveExport() throws IOException {
        // Registries: kontering set, active 2027 prices, one customer with a default kundenummer.
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "0.85");
        Long k = kunde("958935420", "Oslo kommune", "AKTIV", null).id();
        regel(k, "KN-1001", null, null, null);

        // Import.
        var forhaandsvisning = usage.forhaandsvis(JAN, "bruk.csv",
                new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));
        assertThat(forhaandsvisning.kanImporteres()).isTrue();
        var imp = usage.importer(forhaandsvisning);
        assertThat(imp.status()).isEqualTo("VALIDERT");

        // Generate.
        var kj = tjeneste.generer(JAN);
        assertThat(tjeneste.harBlokkerende(kj.id())).isFalse();
        assertThat(tjeneste.fakturaer(kj.id())).hasSize(1);

        // Approve.
        tjeneste.godkjenn(kj.id());
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("GODKJENT");

        // Export.
        eksport.eksporter(kj.id());
        assertThat(tjeneste.hent(kj.id()).status()).isEqualTo("EKSPORTERT");
        var filer = tjeneste.eksportfiler(kj.id());
        assertThat(filer).extracting(Eksportfil::type).containsExactlyInAnyOrder("LG04", "PDF", "CSV", "XLSX");

        // LG04 is valid: 3 lines per order, 4324 chars, amounts sum to the run total.
        var lg04 = filer.stream().filter(f -> "LG04".equals(f.type())).findFirst().orElseThrow();
        List<String> linjer = Arrays.stream(new String(arkiv.hent(lg04.blobUrl()), WIN_1252).split("\n", -1))
                .filter(l -> !l.isEmpty()).toList();
        assertThat(linjer).hasSize(6); // melding line + SMS line → 2 orders
        assertThat(linjer).allSatisfy(l -> assertThat(l).hasSize(4324));
        long ore = 0;
        for (int i = 1; i < linjer.size(); i += 3) {
            ore += Long.parseLong(linjer.get(i).substring(212, 232).trim());
        }
        // 1000 × 0.85 = 850.00 + 250.50 SMS = 1100.50 → 110050 øre.
        assertThat(ore).isEqualTo(110050L);

        // Every step is audited.
        var handlinger = hendelser.findTop200ByOrderByTidspunktDescIdDesc().stream()
                .map(h -> h.handling()).distinct().toList();
        assertThat(handlinger).contains("IMPORTERTE", "GENERERTE", "GODKJENTE", "EKSPORTERTE");
    }
}
