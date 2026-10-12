package no.digdir.forsystem.export;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.Regelbrudd;

/**
 * The LG04 file read back during export does not match the run (specs/002 D3, D7, FR-005a). Thrown
 * before anything is archived, so the export leaves no files and the run stays GODKJENT. A mismatch can
 * only come from a defect in forsystem — the file is built from the run's own lines — so the message
 * tells the user that nothing needs undoing and whom to contact.
 */
public class Lg04Avvik extends Regelbrudd {

    private final String periode;
    private final int antallOrdrerFil;
    private final long sumOreFil;
    private final int antallOrdrerKjoring;
    private final long sumOreKjoring;
    private final List<String> feil;

    public Lg04Avvik(String periode, int antallOrdrerFil, long sumOreFil, int antallOrdrerKjoring,
                     long sumOreKjoring, List<String> feil) {
        super(melding(antallOrdrerFil, sumOreFil, antallOrdrerKjoring, sumOreKjoring, feil));
        this.periode = periode;
        this.antallOrdrerFil = antallOrdrerFil;
        this.sumOreFil = sumOreFil;
        this.antallOrdrerKjoring = antallOrdrerKjoring;
        this.sumOreKjoring = sumOreKjoring;
        this.feil = List.copyOf(feil);
    }

    /** Structured figures for the {@code EKSPORT_STOPPET} audit event. */
    public Map<String, Object> detaljer() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("periode", periode);
        d.put("antallOrdrerFil", antallOrdrerFil);
        d.put("sumOreFil", sumOreFil);
        d.put("antallOrdrerKjoring", antallOrdrerKjoring);
        d.put("sumOreKjoring", sumOreKjoring);
        if (!feil.isEmpty()) {
            d.put("feil", feil);
        }
        return d;
    }

    private static String melding(int ordrerFil, long oreFil, int ordrerKjoring, long oreKjoring, List<String> feil) {
        StringBuilder sb = new StringBuilder("Eksport stoppet: LG04-filen har ")
                .append(ordrerFil).append(" ordrer / ").append(kroner(oreFil))
                .append(", fakturagrunnlaget har ").append(ordrerKjoring).append(" ordrer / ").append(kroner(oreKjoring))
                .append(".");
        if (!feil.isEmpty()) {
            sb.append(" Feil i filen: ").append(String.join("; ", feil.subList(0, Math.min(3, feil.size()))));
            if (feil.size() > 3) {
                sb.append(" (og ").append(feil.size() - 3).append(" til)");
            }
            sb.append('.');
        }
        return sb.append(" Ingen filer er laget, og kjøringen er fortsatt godkjent. Dette er en feil i forsystemet"
                + " — kontakt forsystem-teamet og eksporter på nytt når feilen er rettet.").toString();
    }

    /** øre → «12 345,67» (Norwegian grouping and decimal comma). */
    static String kroner(long ore) {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        s.setMinusSign('-');
        return new DecimalFormat("#,##0.00", s).format(BigDecimal.valueOf(ore, 2));
    }
}
