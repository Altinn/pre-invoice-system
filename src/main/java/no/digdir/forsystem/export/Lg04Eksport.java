package no.digdir.forsystem.export;

import java.nio.charset.Charset;
import java.util.List;

import no.digdir.forsystem.export.lg04.Lg04Ordre;
import no.digdir.forsystem.export.lg04.Lg04Skriver;
import org.springframework.stereotype.Component;

/**
 * Assembles the LG04 file from prepared orders: a header line per order, then a line pair per product,
 * each line terminated by a newline, encoded directly to Windows-1252 (no iconv step — docs/02, docs/04
 * Phase 4). The mapping from a run's invoices to orders lives in {@link EksportService}.
 */
@Component
public class Lg04Eksport {

    static final Charset WIN_1252 = Charset.forName("windows-1252");

    private final Lg04Skriver skriver = new Lg04Skriver();

    public String byggTekst(List<Lg04Ordre> ordrer) {
        StringBuilder sb = new StringBuilder();
        for (Lg04Ordre o : ordrer) {
            sb.append(skriver.renderLinje1(o)).append('\n');
            for (int linjenr = 1; linjenr <= o.linjer().size(); linjenr++) {
                sb.append(skriver.renderLinje2(o, linjenr)).append('\n');
                sb.append(skriver.renderLinje3(o, linjenr)).append('\n');
            }
        }
        return sb.toString();
    }

    public byte[] bygg(List<Lg04Ordre> ordrer) {
        return byggTekst(ordrer).getBytes(WIN_1252);
    }
}
