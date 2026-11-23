package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.Periode;

/**
 * Turns the datavarehus's daily rows into monthly {@link RaaBruksrad}s — one per (orgnr, product,
 * type) — using the maintained product-name mapping. Rows that cannot be attributed are rejected here
 * with a reason; everything else (orgnr format, duplicates, blockers) is the shared pipeline's job.
 *
 * <p>Rules: an unmapped product name is never guessed (OQ-18), one reject per (orgnr, name); a row
 * dated outside the period is rejected; volume mappings sum {@code quantity} (whole, non-negative,
 * no {@code amount_nok}); cost mappings sum {@code amount_nok}.
 */
final class DwhAggregering {

    /** What a DWH product name maps to: a forsystem product code and usage type. */
    record Maal(String produktkode, Bruksdatatype type) {
    }

    record Resultat(List<RaaBruksrad> rader, List<AvvistRad> avviste, List<DwhKildegruppe> kildegrupper) {
    }

    private DwhAggregering() {
    }

    static Resultat aggreger(Periode periode, List<DwhRad> kilde, Map<String, Maal> kobling) {
        Map<String, BigDecimal> summer = new LinkedHashMap<>();
        Map<String, String[]> nøkkelDeler = new LinkedHashMap<>();
        Map<String, Integer> ukjente = new LinkedHashMap<>();
        Map<String, Gruppe> grupper = new LinkedHashMap<>();
        List<AvvistRad> avviste = new ArrayList<>();

        for (DwhRad rad : kilde) {
            String ref = rad.organisasjonsnummer() + "/" + rad.produktnavn() + "/" + rad.dato();
            if (!periode.årMåned().equals(YearMonth.from(rad.dato()))) {
                avviste.add(new AvvistRad(ref, "feil periode (forventet " + periode + ")"));
                continue;
            }
            Maal maal = kobling.get(rad.produktnavn());
            if (maal == null) {
                ukjente.merge(rad.organisasjonsnummer() + "/" + rad.produktnavn(), 1, Integer::sum);
                continue;
            }
            BigDecimal verdi;
            if (maal.type().erVolum()) {
                if (rad.belop() != null) {
                    avviste.add(new AvvistRad(ref, "amount_nok er satt på et volumprodukt"));
                    continue;
                }
                verdi = rad.antall();
                if (verdi == null && rad.erFlagget()) {
                    // A flagged row with NULL quantity is a zero-count day (specs/017).
                    verdi = BigDecimal.ZERO;
                }
                if (verdi == null) {
                    avviste.add(new AvvistRad(ref, "mangler quantity"));
                    continue;
                }
                if (verdi.signum() < 0 || verdi.stripTrailingZeros().scale() > 0) {
                    avviste.add(new AvvistRad(ref, "quantity må være et ikke-negativt heltall: " + verdi));
                    continue;
                }
            } else {
                verdi = rad.belop();
                if (verdi == null) {
                    avviste.add(new AvvistRad(ref, "mangler amount_nok for kostnadstype " + maal.type()));
                    continue;
                }
            }
            if (rad.erFlagget() && maal.type().erVolum() && verdi.signum() == 0) {
                continue; // a zero day neither creates a usage row nor a source group
            }
            String nøkkel = rad.organisasjonsnummer() + "/" + maal.produktkode() + "/" + maal.type();
            if (rad.erFlagget()) {
                String gnøkkel = nøkkel + "/" + rad.kildetabell() + "/" + rad.ressurs();
                Gruppe g = grupper.computeIfAbsent(gnøkkel, k -> new Gruppe(rad.organisasjonsnummer(),
                        maal.produktkode(), maal.type().name(), rad.kildetabell(), rad.ressurs()));
                g.dagsrader++;
                g.sum = g.sum.add(verdi);
                if (rad.regel() != null) {
                    g.regler.add(rad.regel());
                }
            }
            summer.merge(nøkkel, verdi, BigDecimal::add);
            nøkkelDeler.putIfAbsent(nøkkel, new String[]{rad.organisasjonsnummer(), maal.produktkode(), maal.type().name()});
        }

        ukjente.forEach((ref, antall) -> avviste.add(new AvvistRad(ref,
                "ukjent produktnavn i datavarehus: " + ref.substring(ref.indexOf('/') + 1)
                        + " (" + antall + " dagsrader) — legg inn kobling under Produkter → Kildenavn")));

        List<RaaBruksrad> rader = new ArrayList<>();
        String periodeTekst = periode.førsteDag().toString();
        summer.forEach((nøkkel, sum) -> {
            String[] d = nøkkelDeler.get(nøkkel);
            boolean volum = Bruksdatatype.valueOf(d[2]).erVolum();
            rader.add(new RaaBruksrad(nøkkel, periodeTekst, d[0], d[1], d[2],
                    volum ? sum.toPlainString() : null, volum ? null : sum.toPlainString()));
        });
        List<DwhKildegruppe> kildegrupper = grupper.values().stream()
                .map(g -> new DwhKildegruppe(g.orgnr, g.produktkode, g.type, g.kildetabell, g.ressurs,
                        g.dagsrader, g.sum, List.copyOf(g.regler)))
                .toList();
        return new Resultat(rader, avviste, kildegrupper);
    }

    /** Mutable accumulator for one source group while aggregating. */
    private static final class Gruppe {
        final String orgnr;
        final String produktkode;
        final String type;
        final String kildetabell;
        final String ressurs;
        int dagsrader;
        BigDecimal sum = BigDecimal.ZERO;
        final java.util.Set<String> regler = new java.util.LinkedHashSet<>();

        Gruppe(String orgnr, String produktkode, String type, String kildetabell, String ressurs) {
            this.orgnr = orgnr;
            this.produktkode = produktkode;
            this.type = type;
            this.kildetabell = kildetabell;
            this.ressurs = ressurs;
        }
    }
}
