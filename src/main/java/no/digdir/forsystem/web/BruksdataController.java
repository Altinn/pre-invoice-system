package no.digdir.forsystem.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeParseException;

import jakarta.servlet.http.HttpSession;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.BruksdataImport;
import no.digdir.forsystem.usage.Bruksdatafil;
import no.digdir.forsystem.usage.DwhImportService;
import no.digdir.forsystem.usage.Forhaandsvisning;
import no.digdir.forsystem.usage.UsageImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    static final String DWH_FIL = "dwhFil";

    private final UsageImportService service;
    private final DwhImportService dwh;

    BruksdataController(UsageImportService service, DwhImportService dwh) {
        this.service = service;
        this.dwh = dwh;
    }

    @GetMapping
    String liste(Model model) {
        var importer = service.alleImporter();
        model.addAttribute("importer", importer);
        model.addAttribute("dwhAktiv", dwh.aktiv());
        model.addAttribute("venter", importer.stream().filter(i -> "MOTTATT".equals(i.status())).count());
        return "bruksdata/liste";
    }

    /** Fetch the period from the datavarehus and show the same preview as a CSV upload. */
    @PostMapping("/hent-dwh")
    String hentDwh(@RequestParam String periode, HttpSession session, Model model) {
        return visForhaandsvisning(dwh.forhaandsvis(parsePeriode(periode)), session, model);
    }

    /** Confirm a staged (MOTTATT) datavarehus import after re-validating it from its archived payload. */
    @PostMapping("/{id}/bekreft")
    String bekreftStaget(@PathVariable Long id) {
        dwh.bekreftStaget(id);
        return "redirect:/bruksdata";
    }

    /** The exact datavarehus response an import was built from (traceability, FR-006). */
    @GetMapping("/{id}/raadata")
    ResponseEntity<byte[]> raadata(@PathVariable Long id) {
        BruksdataImport imp = service.hentImport(id);
        byte[] innhold = service.raadata(id);
        boolean json = UsageImportService.erJson(innhold);
        return ResponseEntity.ok()
                .contentType(json ? MediaType.APPLICATION_JSON : MediaType.valueOf("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("bruksdata-" + imp.periode() + "-import-" + id + (json ? ".json" : ".csv"))
                        .build().toString())
                .body(innhold);
    }

    /**
     * Upload a usage file. Either forsystem's own CSV format or the datavarehus's export of its view
     * (the fallback while the API is unreachable, OQ-19) — recognised from the header, so there is one
     * upload for both. An export is kept in the session so missing customers can be created from it
     * before confirming.
     */
    @PostMapping("/last-opp")
    String lastOpp(@RequestParam String periode, @RequestParam("fil") MultipartFile fil,
                   HttpSession session, Model model) {
        if (fil == null || fil.isEmpty()) {
            throw new Regelbrudd("Ingen fil ble lastet opp");
        }
        byte[] innhold;
        try {
            innhold = fil.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese filen", e);
        }
        Periode p = parsePeriode(periode);
        if (Bruksdatafil.gjenkjenn(innhold) == Bruksdatafil.Format.DATAVAREHUS) {
            session.setAttribute(DWH_FIL, new DwhFil(p, fil.getOriginalFilename(), innhold));
            return visDwhFil(session, model, null);
        }
        session.removeAttribute(DWH_FIL);
        try {
            return visForhaandsvisning(service.forhaandsvis(p, fil.getOriginalFilename(),
                    new ByteArrayInputStream(innhold)), session, model);
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese filen", e);
        }
    }

    /** Register the uploaded export's organisations that are not customers yet, then show the preview again. */
    @PostMapping("/last-opp/opprett-kunder")
    String opprettKunderFraDwhFil(HttpSession session, Model model) {
        if (!(session.getAttribute(DWH_FIL) instanceof DwhFil f)) {
            throw new Regelbrudd("Ingen opplastet datavarehus-fil. Last den opp på nytt.");
        }
        return visDwhFil(session, model, dwh.opprettManglendeKunder(f.innhold()));
    }

    private String visDwhFil(HttpSession session, Model model, DwhImportService.Kundeoppretting resultat) {
        DwhFil f = (DwhFil) session.getAttribute(DWH_FIL);
        model.addAttribute("manglendeKunder", dwh.manglendeKunder(f.innhold()));
        model.addAttribute("kundeoppretting", resultat);
        return visForhaandsvisning(dwh.forhaandsvisFil(f.periode(), f.filnavn(), f.innhold()), session, model);
    }

    /** An uploaded datavarehus export kept between preview, customer creation and confirm. */
    record DwhFil(Periode periode, String filnavn, byte[] innhold) implements java.io.Serializable {
    }

    private static String visForhaandsvisning(Forhaandsvisning fv, HttpSession session, Model model) {
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
            throw new Regelbrudd("Ingen gyldig forhåndsvisning å bekrefte. Last opp filen eller hent på nytt.");
        }
        service.importer(fv);
        session.removeAttribute(STAGED);
        return "redirect:/bruksdata";
    }

    @GetMapping("/{id}")
    String detalj(@PathVariable Long id, Model model) {
        BruksdataImport imp = service.hentImport(id);
        model.addAttribute("import", imp);
        model.addAttribute("rader", service.rader(id));
        if ("MOTTATT".equals(imp.status()) && "DWH".equals(imp.kilde())) {
            // A staged import shows its preview, rebuilt now from the archived payload.
            model.addAttribute("fv", dwh.forhaandsvisStaget(id));
        }
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
