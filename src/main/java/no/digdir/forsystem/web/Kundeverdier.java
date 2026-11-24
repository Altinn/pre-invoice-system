package no.digdir.forsystem.web;

import java.util.Arrays;
import java.util.stream.Collectors;

import no.digdir.forsystem.usage.Kundeavvik;
import no.digdir.forsystem.usage.KundeavvikBygger;
import org.springframework.stereotype.Component;

/** Shows the before/after customer values of a comparison (specs/019), as {@code @kundeverdier} in templates. */
@Component("kundeverdier")
public class Kundeverdier {

    private static final String INGEN = "(ingen)";

    /** The invoiced values: one, or several when the lines carried different ones. */
    public String fakturert(String[] verdier) {
        if (verdier == null || verdier.length == 0) {
            return INGEN;
        }
        return Arrays.stream(verdier).map(this::en).collect(Collectors.joining(", "));
    }

    public String en(String verdi) {
        String v = KundeavvikBygger.normaliser(verdi);
        return v.isEmpty() ? INGEN : "«" + v + "»";
    }

    /** The bestillingsnummer the new invoice line would carry: the fakturareferanse when none is delivered. */
    public String bestillingsnummer(Kundeavvik avvik) {
        String best = KundeavvikBygger.normaliser(avvik.kandidatBestillingsnummer());
        return en(best.isEmpty() ? avvik.kandidatFakturareferanse() : best);
    }
}
