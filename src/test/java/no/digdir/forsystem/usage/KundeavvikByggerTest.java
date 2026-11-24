package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import no.digdir.forsystem.usage.KjoringStatusPort.FakturertKunde;
import org.junit.jupiter.api.Test;

class KundeavvikByggerTest {

    private static final LocalDate JAN = LocalDate.of(2027, 1, 1);
    private static final String A = "100000001";
    private static final String B = "100000002";
    private static final String C = "100000003";

    @Test
    void groupsProductChangesPerCustomerAndCountsUnchangedCustomers() {
        var diff = BruksdataDifferanse.sammenlign(
                List.of(volum(1L, A, 1L, "100"), volum(2L, A, 2L, "5"), volum(3L, B, 1L, "7"), volum(4L, C, 1L, "1")),
                List.of(volum(11L, A, 1L, "120"), volum(12L, A, 3L, "9"), volum(13L, C, 1L, "1")));

        var resultat = KundeavvikBygger.bygg(diff.endringer(), Set.of(A, B, C), Set.of(A, C),
                Map.of(A, fakturert("Kunde A", "KN1", "REF", "REF"), C, fakturert("Kunde C", "KN3", "", "")),
                Map.of(A, kunde(A, "Kunde A", "KN1", "REF", null), C, kunde(C, "Kunde C", "KN3", null, null)));

        assertThat(resultat.avvik()).extracting(KundeavvikBygger.Utkast::organisasjonsnummer).containsExactly(A, B);
        assertThat(resultat.avvik().get(0).kategorier()).containsExactly(
                Kundeavvikskategori.LAGT_TIL, Kundeavvikskategori.ENDRET, Kundeavvikskategori.FJERNET);
        assertThat(resultat.avvik().get(1).kategorier()).containsExactly(Kundeavvikskategori.FJERNET);
        assertThat(resultat.avvik().get(1).kandidat()).isNull();
        assertThat(resultat.uendredeKunder()).isOne();
    }

    @Test
    void customerValuesThatDifferFromTheInvoiceAreDeviationsWithoutProductChanges() {
        var resultat = KundeavvikBygger.bygg(List.of(), Set.of(A, B), Set.of(A, B),
                Map.of(A, fakturert("Kunde A", "KN1", "Ola", "PO-1"), B, fakturert("Kunde B", "KN2", "X", "X")),
                Map.of(A, kunde(A, "Kunde A", "KN9", "Kari", "PO-2"), B, kunde(B, "kunde b", "KN2", "X", null)));

        assertThat(resultat.avvik()).hasSize(2);
        assertThat(resultat.avvik().get(0).kategorier()).containsExactly(
                Kundeavvikskategori.ENDRET_FAKTURAREFERANSE, Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER,
                Kundeavvikskategori.ENDRET_KUNDENUMMER);
        // Letter case is printed on the invoice, so a case change is a change.
        assertThat(resultat.avvik().get(1).kategorier()).containsExactly(Kundeavvikskategori.ENDRET_NAVN);
        assertThat(resultat.uendredeKunder()).isZero();
    }

    @Test
    void whitespaceEmptyValuesAndTheBestillingsnummerFallbackAreNotChanges() {
        // Invoiced: no bestillingsnummer delivered, so the line carried the fakturareferanse; no name → orgnr.
        var resultat = KundeavvikBygger.bygg(List.of(), Set.of(A, B), Set.of(A, B),
                Map.of(A, fakturert("Kunde A", "KN1", "Ola", "Ola"), B, fakturert(B, "KN2", "", "")),
                Map.of(A, kunde(A, " Kunde A ", "KN1", "Ola  ", ""), B, kunde(B, null, "KN2", " ", null)));

        assertThat(resultat.avvik()).isEmpty();
        assertThat(resultat.uendredeKunder()).isEqualTo(2);
    }

    @Test
    void severalInvoicedReferencesAreAChangeAndAnAddedCustomerIsNotCompared() {
        var resultat = KundeavvikBygger.bygg(List.of(), Set.of(A), Set.of(A, B),
                Map.of(A, new FakturertKunde(List.of("Kunde A"), List.of("KN1"), List.of("Ola", "Per"),
                        List.of("Ola", "Per"))),
                Map.of(A, kunde(A, "Kunde A", "KN1", "Ola", null), B, kunde(B, "Kunde B", "KN2", "Z", null)));

        assertThat(resultat.avvik()).singleElement().satisfies(a -> {
            assertThat(a.organisasjonsnummer()).isEqualTo(A);
            assertThat(a.kategorier()).containsExactly(Kundeavvikskategori.ENDRET_FAKTURAREFERANSE,
                    Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER);
        });
        // B is new in the candidate but has no product change here, so it is not a deviation by itself.
        assertThat(resultat.uendredeKunder()).isOne();
    }

    @Test
    void onlyReferenceAndKundenummerChangesStopADeltaCorrection() {
        assertThat(java.util.Arrays.stream(Kundeavvikskategori.values()).filter(Kundeavvikskategori::hindrerDifferanse))
                .containsExactly(Kundeavvikskategori.ENDRET_FAKTURAREFERANSE,
                        Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER, Kundeavvikskategori.ENDRET_KUNDENUMMER);
    }

    private static FakturertKunde fakturert(String navn, String kundenummer, String ref, String best) {
        return new FakturertKunde(List.of(navn), List.of(kundenummer), List.of(ref), List.of(best));
    }

    private static BruksdataKunde kunde(String orgnr, String navn, String kundenummer, String ref, String best) {
        return new BruksdataKunde(1L, 1L, orgnr, navn, kundenummer, ref, best, new String[0]);
    }

    private static Bruksdata volum(Long id, String orgnr, Long produktId, String antall) {
        return new Bruksdata(id, 1L, JAN, orgnr, produktId, Bruksdatatype.BRUKSVOLUM.name(),
                new BigDecimal(antall), null);
    }
}
