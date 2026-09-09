package no.digdir.forsystem.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeParseException;

import jakarta.servlet.http.HttpSession;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/bruksdata")
class BruksdataController {

    /** Session key for the staged preview between upload and commit. */
    static final String STAGED = "stagetForhaandsvisning";

    private final UsageImportService service;

    BruksdataController(UsageImportService service) {
        this.service = service;
    }

    @GetMapping
    String liste(Model model) {
        model.addAttribute("importer", service.alleImporter());
        return "bruksdata/liste";
    }

    @PostMapping("/forhandsvis")
    String forhandsvis(@RequestParam String periode, @RequestParam("fil") MultipartFile fil,
                       HttpSession session, Model model) {
        if (fil == null || fil.isEmpty()) {
            throw new Regelbrudd("Ingen fil ble lastet opp");
        }
        Periode p = parsePeriode(periode);
        Forhaandsvisning fv;
        try {
            fv = service.forhaandsvis(p, fil.getOriginalFilename(), fil.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese filen", e);
        }
        // Stage only a committable preview; otherwise the confirm step has nothing valid to persist.
        if (fv.kanImporteres()) {
            session.setAttribute(STAGED, fv);
        } else {
            session.removeAttribute(STAGED);
        }
        model.addAttribute("fv", fv);
        return "bruksdata/forhandsvis";
    }

    @PostMapping("/bekreft")
    String bekreft(HttpSession session) {
        Object staged = session.getAttribute(STAGED);
        if (!(staged instanceof Forhaandsvisning fv)) {
            throw new Regelbrudd("Ingen gyldig forhåndsvisning å bekrefte. Last opp filen på nytt.");
        }
        service.importer(fv);
        session.removeAttribute(STAGED);
        return "redirect:/bruksdata";
    }

    @GetMapping("/{id}")
    String detalj(@PathVariable Long id, Model model) {
        model.addAttribute("import", service.hentImport(id));
        model.addAttribute("rader", service.rader(id));
        return "bruksdata/detalj";
    }

    private static Periode parsePeriode(String periode) {
        try {
            return Periode.parse(periode);
        } catch (DateTimeParseException e) {
            throw new Regelbrudd("Ugyldig periode: " + periode + " (forventet ÅÅÅÅ-MM)");
        }
    }
}
