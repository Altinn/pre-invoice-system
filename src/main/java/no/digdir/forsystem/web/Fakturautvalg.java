package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import no.digdir.forsystem.billing.Faktura;

/**
 * One page of a run's invoices for the Fakturaer tab (specs/006 FR-008/009): search, sort and paging
 * done in memory — a run has a few hundred invoices. Bad input falls back to defaults rather than
 * failing, so a shared or edited address always shows something.
 *
 * @param antallTreff invoices matching the search, over all pages
 * @param sumTreff    total amount of those matches
 * @param q           the trimmed search term ({@code ""} when not searching)
 * @param sorter      {@code ordre}, {@code kunde} or {@code belop}
 */
public record Fakturautvalg(List<Rad> rader, int antallTreff, BigDecimal sumTreff, int antallTotalt,
                            int side, int antallSider, String q, String sorter) {

    public static final int PER_SIDE = 50;
    private static final int SIDELENKER = 7;
    private static final int MAKS_SOK = 100;

    public record Rad(Faktura faktura, String kundenavn) {
    }

    /** {@code navn} maps invoice id to the customer name to show (FakturakjoringService.kundenavn). */
    public static Fakturautvalg av(List<Faktura> alle, Map<Long, String> navn, String q, String sorter,
                                   Integer side) {
        String sok = q == null ? "" : q.strip();
        if (sok.length() > MAKS_SOK) {
            sok = sok.substring(0, MAKS_SOK);
        }
        String sorterPaa = switch (sorter == null ? "" : sorter) {
            case "kunde", "belop" -> sorter;
            default -> "ordre";
        };
        String nål = sok.toLowerCase(Locale.ROOT);
        List<Rad> treff = alle.stream()
                .map(f -> new Rad(f, navn.getOrDefault(f.id(), "")))
                .filter(r -> nål.isEmpty() || treffer(r, nål))
                .sorted(rekkefolge(sorterPaa))
                .toList();

        int antallSider = Math.max(1, (treff.size() + PER_SIDE - 1) / PER_SIDE);
        int gjeldende = Math.clamp(side == null ? 1 : side, 1, antallSider);
        int fra = (gjeldende - 1) * PER_SIDE;
        BigDecimal sum = treff.stream().map(r -> r.faktura().sumBelop()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Fakturautvalg(treff.subList(fra, Math.min(fra + PER_SIDE, treff.size())), treff.size(), sum,
                alle.size(), gjeldende, antallSider, sok, sorterPaa);
    }

    public boolean soker() {
        return !q.isEmpty();
    }

    /**
     * The page slots to show between "previous" and "next", at most {@value #SIDELENKER}: page numbers,
     * with {@code 0} for an ellipsis. The same window as Designsystemet's pagination, so the server-rendered
     * links work without JS and match what {@code <ds-pagination>} computes.
     */
    public List<Integer> sider() {
        int vis = Math.min(SIDELENKER, antallSider);
        int halv = (vis - 1) / 2;
        int fra = Math.max(Math.min(side - halv, antallSider - vis + 1), 1);
        int til = Math.min(Math.max(side + (vis - halv - 1), vis), antallSider);
        var sider = new ArrayList<Integer>();
        for (int s = fra; s <= til; s++) {
            sider.add(s);
        }
        if (vis > 4 && fra > 1) {
            sider.set(0, 1);
            sider.set(1, 0);
        }
        if (vis > 3 && til < antallSider) {
            sider.set(sider.size() - 2, 0);
            sider.set(sider.size() - 1, antallSider);
        }
        return sider;
    }

    private static boolean treffer(Rad r, String nål) {
        Faktura f = r.faktura();
        return inneholder(r.kundenavn(), nål)
                || inneholder(f.fakturamottakerOrgnr(), nål)
                || inneholder(f.kundenummer(), nål)
                || (f.ordreNr() != null && f.ordreNr().toString().equals(nål));
    }

    private static boolean inneholder(String felt, String nål) {
        return felt != null && felt.toLowerCase(Locale.ROOT).contains(nål);
    }

    private static Comparator<Rad> rekkefolge(String sorter) {
        Comparator<Rad> etterOrdre = Comparator.comparing(r -> r.faktura().ordreNr(),
                Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sorter) {
            case "kunde" -> {
                Collator norsk = Collator.getInstance(Locale.of("nb", "NO"));
                yield Comparator.comparing(Rad::kundenavn, norsk).thenComparing(etterOrdre);
            }
            case "belop" -> Comparator.comparing((Rad r) -> r.faktura().sumBelop(), Comparator.reverseOrder())
                    .thenComparing(etterOrdre);
            default -> etterOrdre;
        };
    }
}
