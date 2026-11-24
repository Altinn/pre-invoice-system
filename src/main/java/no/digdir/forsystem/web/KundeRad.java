package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.Bruksdatatype;
import no.digdir.forsystem.usage.Kundeavvik;

/**
 * One customer on the page: its deviation, its product changes and their difference in kroner at the price
 * version the correction would use. {@code belop} counts only what can be priced; {@code manglerPris} says
 * whether a volume change has no price (generation then reports MANGLER_PRIS).
 */
public record KundeRad(Kundeavvik avvik, List<BruksdataEndring> endringer, Map<Long, BigDecimal> linjebelop,
                BigDecimal belop, boolean manglerPris, BigDecimal volumdelta, BigDecimal kostnadsdelta) {

    public static KundeRad av(Kundeavvik avvik, List<BruksdataEndring> endringer, Map<Long, BigDecimal> enhetspriser) {
        Map<Long, BigDecimal> linjebelop = new LinkedHashMap<>();
        BigDecimal belop = BigDecimal.ZERO;
        BigDecimal volum = BigDecimal.ZERO;
        BigDecimal kostnad = BigDecimal.ZERO;
        boolean manglerPris = false;
        for (BruksdataEndring e : endringer) {
            BigDecimal linje;
            if (Bruksdatatype.valueOf(e.type()).erVolum()) {
                volum = volum.add(e.delta());
                BigDecimal pris = enhetspriser.get(e.produktId());
                if (pris == null) {
                    manglerPris = true;
                    continue;
                }
                // Rounded per line, as generation does.
                linje = e.delta().multiply(pris).setScale(2, RoundingMode.HALF_UP);
            } else {
                kostnad = kostnad.add(e.delta());
                linje = e.delta().setScale(2, RoundingMode.HALF_UP);
            }
            linjebelop.put(e.id(), linje);
            belop = belop.add(linje);
        }
        return new KundeRad(avvik, List.copyOf(endringer), linjebelop, belop, manglerPris, volum, kostnad);
    }

    /** Sign of the net difference; with unpriced changes, the sign of the summed deltas. */
    public int fortegn() {
        if (belop.signum() != 0 || !manglerPris) {
            return belop.signum();
        }
        return volumdelta.add(kostnadsdelta).signum();
    }

    /** Whether a delta correction can take this customer: it has product changes and no blocking change. */
    public boolean kanVelges() {
        return !avvik.erAvvist() && !endringer.isEmpty() && !hindrerDifferanse();
    }

    public boolean hindrerDifferanse() {
        return !avvik.hindrerDifferanse().isEmpty();
    }
}
