package no.digdir.forsystem.export.lg04;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads an LG04 file back to count its orders and sum its amounts, so an export can be reconciled
 * against its run before it leaves forsystem (specs/002-pre-approval-control D3–D4).
 *
 * <p>Deliberately independent of {@link Lg04Skriver}: its offsets are its own constants, taken from the
 * reference format and pinned by the golden files, and it never calls the writer. A reader that shared
 * the writer's code would share its bugs, and the check would prove nothing.
 *
 * <p>An order is a header line (line number 0) followed by one line pair per product, numbered 1, 2, 3…
 * (OQ-1). Any structural problem (wrong line length, a pair out of sequence, disagreeing order numbers or
 * amount fields, unparseable amount) is reported in {@link Lg04Innhold#feil()} and makes the file invalid; the caller
 * treats an invalid file as a mismatch.
 */
public final class Lg04Leser {

    private static final Charset WIN_1252 = Charset.forName("windows-1252");

    static final int LINJELENGDE = 4324;
    private static final int LINJENR_FRA = 2493;          // 0 on the header, 1, 2, 3… on each product's pair
    private static final int LINJENR_TIL = 2509;
    private static final int BELOP_FRA = 212;             // amount in øre, right-aligned, then BELOP_SATT at 232
    private static final int BELOP_TIL = 232;
    private static final int BELOP_SATT = 232;            // '1' on the amount line only
    private static final int BELOP2_FRA = 965;            // the same amount again, 20 wide, right-aligned
    private static final int BELOP2_TIL = 985;
    private static final int ORDRENR_FRA = 3130;
    private static final int ORDRENR_TIL = 3145;

    /** What the file contains: orders, the øre sum of their amount lines, and any structural errors. */
    public record Lg04Innhold(int antallOrdrer, long sumOre, int antallLinjer, List<String> feil) {

        public Lg04Innhold {
            feil = List.copyOf(feil);
        }

        public boolean gyldig() {
            return feil.isEmpty();
        }
    }

    public Lg04Innhold les(byte[] windows1252) {
        String tekst = new String(windows1252, WIN_1252);
        List<String> linjer = new ArrayList<>(Arrays.asList(tekst.split("\n", -1)));
        if (!linjer.isEmpty() && linjer.get(linjer.size() - 1).isEmpty()) {
            linjer.remove(linjer.size() - 1);   // the file ends with a newline
        }

        List<String> feil = new ArrayList<>();
        for (int i = 0; i < linjer.size(); i++) {
            if (linjer.get(i).length() != LINJELENGDE) {
                feil.add("Linje " + (i + 1) + " har " + linjer.get(i).length() + " tegn, forventet " + LINJELENGDE);
            }
        }
        if (!feil.isEmpty()) {
            return new Lg04Innhold(0, 0, linjer.size(), feil);
        }

        int ordrer = 0;
        int ordre = 0;
        long sumOre = 0;
        int i = 0;
        while (i < linjer.size()) {
            String hode = linjer.get(i);
            ordre++;
            if (!"0".equals(felt(hode, LINJENR_FRA, LINJENR_TIL))) {
                feil.add("Linje " + (i + 1) + " skulle vært hodelinjen til ordre " + ordre + " (linjenummer 0)");
                break;
            }
            String nr = felt(hode, ORDRENR_FRA, ORDRENR_TIL);
            int forste = i;
            i++;
            int produkt = 0;
            boolean ok = true;
            while (i < linjer.size() && !"0".equals(felt(linjer.get(i), LINJENR_FRA, LINJENR_TIL))) {
                produkt++;
                if (i + 1 >= linjer.size()) {
                    feil.add("Ordre " + ordre + ": produkt " + produkt + " mangler den andre linjen i paret");
                    ok = false;
                    i++;
                    break;
                }
                String belop = linjer.get(i);
                String slutt = linjer.get(i + 1);
                i += 2;
                String forventet = String.valueOf(produkt);
                if (!forventet.equals(felt(belop, LINJENR_FRA, LINJENR_TIL))
                        || !forventet.equals(felt(slutt, LINJENR_FRA, LINJENR_TIL))) {
                    feil.add("Ordre " + ordre + ": produkt " + produkt + " har feil linjenummer, forventet "
                            + forventet + " på begge linjene");
                    ok = false;
                    continue;
                }
                if (belop.charAt(BELOP_SATT) != '1' || slutt.charAt(BELOP_SATT) == '1') {
                    feil.add("Ordre " + ordre + ", produkt " + produkt + ": beløpslinjen er ikke første linje i paret");
                    ok = false;
                    continue;
                }
                if (!nr.equals(felt(belop, ORDRENR_FRA, ORDRENR_TIL)) || !nr.equals(felt(slutt, ORDRENR_FRA, ORDRENR_TIL))) {
                    feil.add("Ordre " + ordre + ", produkt " + produkt + ": ordrenummeret er ulikt hodelinjens");
                    ok = false;
                    continue;
                }
                String a = felt(belop, BELOP_FRA, BELOP_TIL);
                String b = felt(belop, BELOP2_FRA, BELOP2_TIL);
                if (!a.equals(b)) {
                    feil.add("Ordre " + ordre + ", produkt " + produkt + ": beløpsfeltene er ulike (" + a + " og " + b + ")");
                    ok = false;
                    continue;
                }
                try {
                    sumOre = Math.addExact(sumOre, Long.parseLong(a));
                } catch (NumberFormatException | ArithmeticException e) {
                    feil.add("Ordre " + ordre + ", produkt " + produkt + ": beløpet «" + a + "» er ikke et gyldig tall i øre");
                    ok = false;
                }
            }
            if (produkt == 0) {
                feil.add("Ordre " + ordre + " (linje " + (forste + 1) + ") har ingen produktlinjer");
                ok = false;
            }
            if (ok) {
                ordrer++;
            }
        }
        return new Lg04Innhold(ordrer, sumOre, linjer.size(), feil);
    }

    private static String felt(String linje, int fra, int til) {
        return linje.substring(fra, til).trim();
    }
}
