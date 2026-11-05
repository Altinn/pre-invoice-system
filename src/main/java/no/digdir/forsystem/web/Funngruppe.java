package no.digdir.forsystem.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.billing.Kontrollfunn;

/**
 * Control findings of one code and severity, shown as one row on the run page (specs/005 FR-006/007):
 * thirty identical warnings read as one line saying 30, and the blocking ones are never buried. The group
 * keeps its findings so each can be accepted on its own, and the row can say how many are (specs/013).
 *
 * @param funn every finding in the group, in the order the findings were recorded
 */
public record Funngruppe(String alvorlighet, String kode, List<Kontrollfunn> funn) {

    private static final Comparator<Funngruppe> REKKEFOLGE = Comparator
            .comparing((Funngruppe g) -> !g.blokkerende())
            .thenComparing(Funngruppe::antall, Comparator.reverseOrder())
            .thenComparing(Funngruppe::kode);

    /** Groups by (severity, code), ordered blocking first, then largest group, then code. */
    public static List<Funngruppe> av(List<Kontrollfunn> funn) {
        Map<List<String>, List<Kontrollfunn>> grupper = new LinkedHashMap<>();
        for (Kontrollfunn f : funn) {
            grupper.computeIfAbsent(List.of(f.alvorlighet(), f.kode()), k -> new ArrayList<>()).add(f);
        }
        return grupper.entrySet().stream()
                .map(e -> new Funngruppe(e.getKey().get(0), e.getKey().get(1), List.copyOf(e.getValue())))
                .sorted(REKKEFOLGE)
                .toList();
    }

    public int antall() {
        return funn.size();
    }

    public long antallAkseptert() {
        return funn.stream().filter(Kontrollfunn::erAkseptert).count();
    }

    /** Every finding in the group is accepted — the group checkbox is ticked. */
    public boolean alleAkseptert() {
        return antallAkseptert() == antall();
    }

    /** Some, not all, are accepted — the row says «N av M akseptert». */
    public boolean delvisAkseptert() {
        long n = antallAkseptert();
        return n > 0 && n < antall();
    }

    public boolean blokkerende() {
        return "BLOKKERENDE".equals(alvorlighet);
    }

    public List<String> meldinger() {
        return funn.stream().map(Kontrollfunn::melding).toList();
    }

    public Kontrollfunn forste() {
        return funn.getFirst();
    }

    public String eksempel() {
        return forste().melding();
    }
}
