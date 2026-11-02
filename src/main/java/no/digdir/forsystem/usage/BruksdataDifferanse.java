package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import no.digdir.forsystem.common.Regelbrudd;

/**
 * Pure, deterministic comparison of two complete normalized usage snapshots. Source snapshots stay
 * non-negative; signed values exist only in the returned differences.
 */
public final class BruksdataDifferanse {

    private static final BigDecimal NULL = BigDecimal.ZERO;

    private BruksdataDifferanse() {
    }

    public static Resultat sammenlign(List<Bruksdata> gammelt, List<Bruksdata> nytt) {
        Map<BruksdataNokkel, Bruksdata> gamle = perNokkel(gammelt, "gammelt grunnlag");
        Map<BruksdataNokkel, Bruksdata> nye = perNokkel(nytt, "nytt grunnlag");
        TreeSet<BruksdataNokkel> nokler = new TreeSet<>(gamle.keySet());
        nokler.addAll(nye.keySet());

        List<Endring> endringer = new ArrayList<>();
        int uendret = 0;
        for (BruksdataNokkel nokkel : nokler) {
            Bruksdata gammel = gamle.get(nokkel);
            Bruksdata ny = nye.get(nokkel);
            boolean volum = Bruksdatatype.valueOf(nokkel.type()).erVolum();
            BigDecimal gammelVerdi = verdi(gammel, volum);
            BigDecimal nyVerdi = verdi(ny, volum);
            if (gammelVerdi.compareTo(nyVerdi) == 0) {
                uendret++;
                continue;
            }
            Endringstype endringstype = gammel == null ? Endringstype.TILAGT
                    : ny == null ? Endringstype.FJERNET : Endringstype.ENDRET;
            BigDecimal delta = nyVerdi.subtract(gammelVerdi);
            endringer.add(new Endring(nokkel, endringstype,
                    gammel == null ? null : gammel.id(), ny == null ? null : ny.id(),
                    volum ? verdiEllerNull(gammel, true) : null,
                    volum ? verdiEllerNull(ny, true) : null,
                    volum ? delta : null,
                    volum ? null : verdiEllerNull(gammel, false),
                    volum ? null : verdiEllerNull(ny, false),
                    volum ? null : delta));
        }
        return new Resultat(List.copyOf(endringer), uendret);
    }

    private static Map<BruksdataNokkel, Bruksdata> perNokkel(List<Bruksdata> rader, String navn) {
        Map<BruksdataNokkel, Bruksdata> resultat = new LinkedHashMap<>();
        for (Bruksdata rad : rader) {
            BruksdataNokkel nokkel = BruksdataNokkel.fra(rad);
            if (resultat.putIfAbsent(nokkel, rad) != null) {
                throw new Regelbrudd("Duplikat i " + navn + ": " + nokkel);
            }
        }
        return resultat;
    }

    private static BigDecimal verdi(Bruksdata rad, boolean volum) {
        BigDecimal verdi = verdiEllerNull(rad, volum);
        return verdi == null ? NULL : verdi;
    }

    private static BigDecimal verdiEllerNull(Bruksdata rad, boolean volum) {
        return rad == null ? null : (volum ? rad.antall() : rad.belop());
    }

    public record Resultat(List<Endring> endringer, int uendredeRader) {
    }

    public record Endring(
            BruksdataNokkel nokkel,
            Endringstype endringstype,
            Long gammelBruksdataId,
            Long nyBruksdataId,
            BigDecimal gammeltAntall,
            BigDecimal nyttAntall,
            BigDecimal deltaAntall,
            BigDecimal gammeltBelop,
            BigDecimal nyttBelop,
            BigDecimal deltaBelop) {

        public BigDecimal gammelVerdi() {
            return deltaAntall != null ? gammeltAntall : gammeltBelop;
        }

        public BigDecimal nyVerdi() {
            return deltaAntall != null ? nyttAntall : nyttBelop;
        }

        public BigDecimal delta() {
            return deltaAntall != null ? deltaAntall : deltaBelop;
        }
    }
}
