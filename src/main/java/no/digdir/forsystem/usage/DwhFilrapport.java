package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * What became of every row of a flagged export, for the preview (specs/017 FR-007). The classes
 * balance: {@code brukt + utenforPerioden + dpv + utelatt + avvist == totalt} (SC-003); {@code avvist} are
 * rows the reader rejected (an invalid flag, or a flagged row without a usable orgnr or quantity).
 *
 * @param utenforPerMaaned rows per other month
 * @param volum             summed quantity per class and DWH product name
 * @param utelattPerArsak   excluded rows per reason, the reason being the file's own {@code ekskludert…} text
 * @param utelattVolumPerOrg excluded quantity per organisation («ukjent orgnr» when it has no valid one)
 */
public record DwhFilrapport(
        int totalt,
        int brukt,
        int utenforPerioden,
        int dpv,
        int utelatt,
        int avvist,
        Map<YearMonth, Integer> utenforPerMaaned,
        Map<DwhRadklasse, Map<String, BigDecimal>> volum,
        Map<String, Integer> utelattPerArsak,
        Map<String, BigDecimal> utelattVolumPerOrg) {

    public boolean balanserer() {
        return brukt + utenforPerioden + dpv + utelatt + avvist == totalt;
    }
}
