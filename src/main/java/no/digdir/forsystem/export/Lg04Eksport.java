package no.digdir.forsystem.export;

import java.nio.charset.Charset;
import java.util.List;

import no.digdir.forsystem.export.lg04.Lg04Ordre;
import no.digdir.forsystem.export.lg04.Lg04Skriver;
import org.springframework.stereotype.Component;

/**
 * Assembles the LG04 file from prepared orders: three lines per order, each terminated by a newline,
 * encoded directly to Windows-1252 (no iconv step — docs/02, docs/04 Phase 4). The mapping from a
 * run's invoice lines to orders (one order per line by default — the OQ-1-safe interpretation) lives
 * in {@link EksportService}.
 */
@Component
public class Lg04Eksport {

    static final Charset WIN_1252 = Charset.forName("windows-1252");

    private final Lg04Skriver skriver = new Lg04Skriver();

    public String byggTekst(List<Lg04Ordre> ordrer) {
        StringBuilder sb = new StringBuilder();
        for (Lg04Ordre o : ordrer) {
            sb.append(skriver.renderLinje1(o)).append('\n');
            sb.append(skriver.renderLinje2(o)).append('\n');
            sb.append(skriver.renderLinje3(o)).append('\n');
        }
        return sb.toString();
    }

    public byte[] bygg(List<Lg04Ordre> ordrer) {
        return byggTekst(ordrer).getBytes(WIN_1252);
    }
}
