package no.digdir.forsystem.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import no.digdir.forsystem.usage.Bruksdatatype;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Økonomi's review: one number format, eksl. mva on every amount, and texts that explain (specs/010). */
@AutoConfigureMockMvc
@WithMockUser(roles = {"LESER", "FORVALTER", "GODKJENNER"})
class TekstOgTallformatIT extends BillingFixture {

    @Autowired MockMvc mvc;
    @Autowired FakturaRepository fakturaer;

    /** One customer, 1000 meldinger at 1 234,56 kr: an invoice of 1 234 560,00 kr. */
    private Fakturakjoring storKjoring() {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1234.5600");
        Long imp = nyImport(JAN);
        Long k = kunde("100000001", "Alfa kommune", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        referanse(k, null, "REF-ALFA", "BEST-9");
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1000", null);
        return tjeneste.generer(JAN);
    }

    private String hent(String url, Object... args) throws Exception {
        return mvc.perform(get(url, args)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void amountsUseOneFormatAndSayEksklMva() throws Exception {
        var kj = storKjoring();

        String liste = hent("/kjoringer");
        assertThat(liste).contains("1 234 560,00", "Sum (eksl. mva)");
        assertThat(hent("/kjoringer/{id}", kj.id())).contains("1 234 560,00", "Sum beløp eksl. mva");
        assertThat(hent("/kjoringer/{id}/fakturaer", kj.id())).contains("1 234 560,00", "Sum (eksl. mva)");
        assertThat(liste).doesNotContain("1234560.00");
    }

    @Test
    void invoicePageShowsCustomerReferenceOnceAndASumRow() throws Exception {
        var kj = storKjoring();
        Faktura f = fakturaer.findByKjoringIdOrderByOrdreNr(kj.id()).getFirst();

        String side = hent("/kjoringer/{id}/faktura/{fid}", kj.id(), f.id());
        assertThat(side).contains("Alfa kommune", "Referanse:", "REF-ALFA / BEST-9");
        assertThat(side).doesNotContain("<th>Ref.</th>");
        assertThat(side).contains("1 000,00", "1 234,56", "Beløp (eksl. mva)");
        String fot = side.substring(side.indexOf("<tfoot"), side.indexOf("</tfoot>"));
        assertThat(fot).contains("Sum eksl. mva", "1 234 560,00");
    }

    @Test
    void aSplitInvoiceListsEveryReferenceIncludingNone() throws Exception {
        settKontoAlle();
        Long v = aktivVersjon(LocalDate.of(2027, 1, 1), null).id();
        pris(v, "melding", "1.0000");
        pris(v, "formidling", "1.0000");
        Long imp = nyImport(JAN);
        Long k = kunde("100000001", "Alfa kommune", "AKTIV", null).id();
        regel(k, "KN1", null, null, null);
        referanse(k, "formidling", "REF-F", null);  // product-specific; melding has none
        bruk(imp, JAN, "100000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);
        bruk(imp, JAN, "100000001", "formidling", Bruksdatatype.BRUKSVOLUM, "1", null);
        var kj = tjeneste.generer(JAN);
        Faktura f = fakturaer.findByKjoringIdOrderByOrdreNr(kj.id()).getFirst();

        String side = hent("/kjoringer/{id}/faktura/{fid}", kj.id(), f.id());
        String ref = side.substring(side.indexOf("Referanse:"), side.indexOf("</p>", side.indexOf("Referanse:")));
        assertThat(ref).contains("REF-F", "ingen referanse");
    }

    @Test
    void kontrollerExplainsSeveritiesAndEveryCode() throws Exception {
        var kj = storKjoring();

        String side = hent("/kjoringer/{id}/kontroller", kj.id());
        assertThat(side).contains("Hva betyr alvorlighet?", "Hva betyr kodene?", "hindrer ikke");
        for (Kontrollkode k : Kontrollkode.values()) {
            assertThat(side).contains(k.name(), k.definisjon());
        }
    }

    @Test
    void findingsColumnIsCalledBeskrivelse() throws Exception {
        settKontoAlle();
        aktivVersjon(LocalDate.of(2027, 1, 1), null);
        Long imp = nyImport(JAN);
        bruk(imp, JAN, "900000001", "melding", Bruksdatatype.BRUKSVOLUM, "1", null);  // MANGLER_KUNDE
        var kj = tjeneste.generer(JAN);

        String side = hent("/kjoringer/{id}/kontroller", kj.id());
        assertThat(side).contains("<th>Beskrivelse</th>").doesNotContain("Eksempel");
    }

    @Test
    void avstemmingBruksdataSaysWhatItChecks() throws Exception {
        var kj = storKjoring();

        String side = hent("/kjoringer/{id}/kontroller", kj.id());
        assertThat(side).contains(
                "Denne avstemmingen brukes for å kontrollere at alle bruksdata har kommet med på",
                "Her hensyntas ikke prisliste eller kostnad som skal faktureres.",
                "Importerte rader fra datavarehus", "Importert bruksdata fra datavarehus",
                "Fakturerte linjer", "Fakturert bruksdata", "1 000,00");
        assertThat(side).doesNotContain("Importert sum", "Fakturert sum");
    }

    @Test
    void bruksdataAndKjoringerPointToTheNextStep() throws Exception {
        assertThat(hent("/bruksdata")).contains("Neste steg", "href=\"/kjoringer\"");
        assertThat(hent("/kjoringer")).contains("må være importert eller hentet", "href=\"/bruksdata\"");
    }

    @Test
    void previewNamesProductsFormatsNumbersAndWarnsBeforeConfirming() throws Exception {
        kunde("100000001", "Alfa kommune", "AKTIV", null);
        String csv = "periode,organisasjonsnummer,produktkode,type,antall,belop\n"
                + "2027-02-01,100000001,melding,BRUKSVOLUM,1234,\n";
        var fil = new MockMultipartFile("fil", "bruk.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        String side = mvc.perform(multipart("/bruksdata/last-opp").file(fil).param("periode", "2027-02").with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String summer = side.substring(side.indexOf("Summer per produkt"), side.indexOf("</table>", side.indexOf("Summer per produkt")));
        assertThat(summer).contains("<td>Melding</td>", "1 234,00", "Sum beløp (eksl. mva)");
        assertThat(side).contains("Importen kan ikke slettes eller endres etter at importen er bekreftet.");
        assertThat(side.indexOf("Importen kan ikke slettes")).isLessThan(side.indexOf("Bekreft import</button>"));
    }
}
