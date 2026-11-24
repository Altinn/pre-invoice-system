package no.digdir.forsystem.usage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The customer values a usage source delivered for one organisation (specs/018): name, kundenummer,
 * fakturareferanse, bestillingsnummer. Built by {@link Samler} from every used row of the source, so that
 * rows disagreeing on a value are recorded in {@code motstridende} rather than silently resolved (research
 * D2). Blank values are «not delivered», not a conflict; a different name is not a conflict either (the
 * first is kept), since it never reaches Unit4.
 */
public record Kundedata(
        String organisasjonsnummer,
        String navn,
        String kundenummer,
        String fakturareferanse,
        String bestillingsnummer,
        List<String> motstridende) {

    public Kundedata {
        motstridende = List.copyOf(motstridende);
    }

    public boolean harMotstridende() {
        return !motstridende.isEmpty();
    }

    /** Collects the customer values per orgnr, in first-seen order. */
    public static final class Samler {

        private final Map<String, Felt> perOrgnr = new LinkedHashMap<>();

        public Samler legg(String orgnr, String navn, String kundenummer, String fakturareferanse,
                           String bestillingsnummer) {
            if (orgnr == null) {
                return this;
            }
            Felt f = perOrgnr.computeIfAbsent(orgnr, o -> new Felt());
            if (f.navn == null) {
                f.navn = tomTilNull(navn);
            }
            leggTil(f.kundenummer, kundenummer);
            leggTil(f.fakturareferanse, fakturareferanse);
            leggTil(f.bestillingsnummer, bestillingsnummer);
            return this;
        }

        public List<Kundedata> resultat() {
            List<Kundedata> ut = new ArrayList<>();
            perOrgnr.forEach((orgnr, f) -> {
                List<String> motstridende = new ArrayList<>();
                konflikt("kundenummer", f.kundenummer, motstridende);
                konflikt("fakturareferanse", f.fakturareferanse, motstridende);
                konflikt("bestillingsnummer", f.bestillingsnummer, motstridende);
                ut.add(new Kundedata(orgnr, f.navn, forste(f.kundenummer), forste(f.fakturareferanse),
                        forste(f.bestillingsnummer), motstridende));
            });
            return ut;
        }

        private static void leggTil(Set<String> verdier, String verdi) {
            String v = tomTilNull(verdi);
            if (v != null) {
                verdier.add(v);
            }
        }

        private static void konflikt(String felt, Set<String> verdier, List<String> ut) {
            if (verdier.size() > 1) {
                ut.add(felt + ": " + String.join(" | ", verdier));
            }
        }

        private static String forste(Set<String> verdier) {
            return verdier.isEmpty() ? null : verdier.iterator().next();
        }

        private static String tomTilNull(String s) {
            return s == null || s.isBlank() ? null : s.strip();
        }

        private static final class Felt {
            String navn;
            final Set<String> kundenummer = new LinkedHashSet<>();
            final Set<String> fakturareferanse = new LinkedHashSet<>();
            final Set<String> bestillingsnummer = new LinkedHashSet<>();
        }
    }
}
