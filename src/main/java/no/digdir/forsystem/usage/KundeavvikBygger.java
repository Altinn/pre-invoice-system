package no.digdir.forsystem.usage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure grouping of a comparison per customer (specs/019 FR-001..FR-007). A customer is a deviation if any of its
 * products changed, or if the customer values it was invoiced with differ from what the candidate delivered:
 * a changed fakturareferanse has a transaction difference of 0 and must still show up.
 */
public final class KundeavvikBygger {

    private KundeavvikBygger() {
    }

    /**
     * @param endringer  the product changes of the comparison
     * @param gamleOrgnr organisations in the effective invoiced basis
     * @param nyeOrgnr   organisations in the candidate
     * @param fakturert  what each organisation was last invoiced with
     * @param kandidat   the candidate's customer values per organisation
     */
    public static Resultat bygg(List<BruksdataDifferanse.Endring> endringer, Set<String> gamleOrgnr,
                                Set<String> nyeOrgnr, Map<String, KjoringStatusPort.FakturertKunde> fakturert,
                                Map<String, BruksdataKunde> kandidat) {
        Map<String, EnumSet<Kundeavvikskategori>> perOrgnr = new LinkedHashMap<>();
        for (BruksdataDifferanse.Endring e : endringer) {
            perOrgnr.computeIfAbsent(e.nokkel().organisasjonsnummer(), o -> EnumSet.noneOf(Kundeavvikskategori.class))
                    .add(Kundeavvikskategori.fra(e.endringstype()));
        }
        TreeSet<String> alle = new TreeSet<>(gamleOrgnr);
        alle.addAll(nyeOrgnr);
        List<Utkast> avvik = new ArrayList<>();
        for (String orgnr : alle) {
            EnumSet<Kundeavvikskategori> kategorier = perOrgnr.getOrDefault(orgnr,
                    EnumSet.noneOf(Kundeavvikskategori.class));
            KjoringStatusPort.FakturertKunde f = fakturert.get(orgnr);
            BruksdataKunde k = nyeOrgnr.contains(orgnr) ? kandidat.get(orgnr) : null;
            // Customer values are compared only where both sides exist: an added customer was never invoiced, and
            // a removed one has nothing new to compare with.
            if (f != null && k != null) {
                String referanse = normaliser(k.fakturareferanse());
                // The invoice line carries the fakturareferanse when no bestillingsnummer is delivered (specs/012
                // FR-005), so that is the value to compare with what was invoiced.
                String bestillingsnummer = normaliser(k.bestillingsnummer()).isEmpty()
                        ? referanse : normaliser(k.bestillingsnummer());
                // The invoice is named after the orgnr when the DWH delivers no name.
                String navn = normaliser(k.virksomhetsnavn()).isEmpty() ? orgnr : normaliser(k.virksomhetsnavn());
                // An invoice without lines carries no reference to compare with.
                boolean harLinjer = !f.fakturareferanse().isEmpty();
                if (harLinjer && avviker(f.fakturareferanse(), referanse)) {
                    kategorier.add(Kundeavvikskategori.ENDRET_FAKTURAREFERANSE);
                }
                if (harLinjer && avviker(f.bestillingsnummer(), bestillingsnummer)) {
                    kategorier.add(Kundeavvikskategori.ENDRET_BESTILLINGSNUMMER);
                }
                if (avviker(f.kundenummer(), normaliser(k.kundenummer()))) {
                    kategorier.add(Kundeavvikskategori.ENDRET_KUNDENUMMER);
                }
                if (avviker(f.navn(), navn)) {
                    kategorier.add(Kundeavvikskategori.ENDRET_NAVN);
                }
            }
            if (kategorier.isEmpty()) {
                continue;
            }
            avvik.add(new Utkast(orgnr, kategorier,
                    f == null ? List.of() : f.navn(), f == null ? List.of() : f.kundenummer(),
                    f == null ? List.of() : f.fakturareferanse(), f == null ? List.of() : f.bestillingsnummer(),
                    k));
        }
        return new Resultat(List.copyOf(avvik), alle.size() - avvik.size());
    }

    /** Leading and trailing whitespace and «empty vs missing» are not changes; anything else is (FR-004). */
    public static String normaliser(String verdi) {
        return verdi == null ? "" : verdi.strip();
    }

    private static boolean avviker(List<String> fakturert, String na) {
        return !(fakturert.size() == 1 && fakturert.getFirst().equals(na));
    }

    public record Resultat(List<Utkast> avvik, int uendredeKunder) {
    }

    public record Utkast(
            String organisasjonsnummer,
            Set<Kundeavvikskategori> kategorier,
            List<String> fakturertNavn,
            List<String> fakturertKundenummer,
            List<String> fakturertFakturareferanse,
            List<String> fakturertBestillingsnummer,
            BruksdataKunde kandidat) {

        public Kundeavvik tilKundeavvik(Long sammenligningId) {
            return new Kundeavvik(null, sammenligningId, organisasjonsnummer,
                    fakturertNavn.toArray(String[]::new), fakturertKundenummer.toArray(String[]::new),
                    fakturertFakturareferanse.toArray(String[]::new), fakturertBestillingsnummer.toArray(String[]::new),
                    kandidat == null ? null : kandidat.virksomhetsnavn(),
                    kandidat == null ? null : kandidat.kundenummer(),
                    kandidat == null ? null : kandidat.fakturareferanse(),
                    kandidat == null ? null : kandidat.bestillingsnummer(),
                    kategorier.stream().map(Enum::name).toArray(String[]::new), null, null, null);
        }
    }
}
