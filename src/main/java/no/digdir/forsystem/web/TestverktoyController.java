package no.digdir.forsystem.web;

import java.time.format.DateTimeParseException;

import no.digdir.forsystem.billing.NullstillingService;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Test-environment tools (local, tt02). Like {@link NullstillingService}, the controller only exists
 * when {@code forsystem.testverktoy.enabled=true}; elsewhere the URL is not mapped at all.
 */
@Controller
@RequestMapping("/testverktoy")
@ConditionalOnProperty(prefix = "forsystem.testverktoy", name = "enabled", havingValue = "true")
class TestverktoyController {

    private final NullstillingService nullstilling;

    TestverktoyController(NullstillingService nullstilling) {
        this.nullstilling = nullstilling;
    }

    @PostMapping("/nullstill-periode")
    String nullstillPeriode(@RequestParam String periode, RedirectAttributes redirect) {
        Periode p;
        try {
            p = Periode.parse(periode);
        } catch (DateTimeParseException e) {
            throw new Regelbrudd("Ugyldig periode: " + periode + " (forventet ÅÅÅÅ-MM)");
        }
        var n = nullstilling.nullstill(p);
        redirect.addFlashAttribute("nullstilt",
                "%s er nullstilt. Slettet: kjøringer %d, fakturaer %d, bruksdataimporter %d, arkiverte filer %d."
                        .formatted(storForbokstav(p.norskLabel()), n.kjoringer(), n.fakturaer(), n.importer(),
                                n.filer()));
        return "redirect:/kjoringer";
    }

    private static String storForbokstav(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
