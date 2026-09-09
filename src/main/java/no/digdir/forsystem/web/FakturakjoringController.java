package no.digdir.forsystem.web;

import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.billing.Eksportfil;
import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.registry.ProduktService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kjoringer")
class FakturakjoringController {

    private final FakturakjoringService kjoringer;
    private final ProduktService produkter;
    private final EksportService eksport;
    private final FileArchive arkiv;

    FakturakjoringController(FakturakjoringService kjoringer, ProduktService produkter,
                             EksportService eksport, FileArchive arkiv) {
        this.kjoringer = kjoringer;
        this.produkter = produkter;
        this.eksport = eksport;
        this.arkiv = arkiv;
    }

    @GetMapping
    String liste(Model model) {
        model.addAttribute("kjoringer", kjoringer.alle());
        return "kjoring/liste";
    }

    @PostMapping
    String generer(@RequestParam String periode) {
        Periode p;
        try {
            p = Periode.parse(periode);
        } catch (DateTimeParseException e) {
            throw new Regelbrudd("Ugyldig periode: " + periode + " (forventet ÅÅÅÅ-MM)");
        }
        var kjoring = kjoringer.generer(p);
        return "redirect:/kjoringer/" + kjoring.id();
    }

    @GetMapping("/{id}")
    String detalj(@PathVariable Long id, Model model) {
        var funn = kjoringer.funn(id);
        model.addAttribute("kjoring", kjoringer.hent(id));
        model.addAttribute("fakturaer", kjoringer.fakturaer(id));
        model.addAttribute("blokkerende", funn.stream()
                .filter(f -> "BLOKKERENDE".equals(f.alvorlighet())).toList());
        model.addAttribute("advarsler", funn.stream()
                .filter(f -> "ADVARSEL".equals(f.alvorlighet())).toList());
        model.addAttribute("harBlokkerende", kjoringer.harBlokkerende(id));
        model.addAttribute("eksportfiler", kjoringer.eksportfiler(id));
        return "kjoring/detalj";
    }

    @PostMapping("/{id}/eksporter")
    String eksporter(@PathVariable Long id) {
        eksport.eksporter(id);
        return "redirect:/kjoringer/" + id;
    }

    @GetMapping("/{id}/eksportfil/{filId}")
    ResponseEntity<byte[]> lastNed(@PathVariable Long id, @PathVariable Long filId) {
        Eksportfil fil = kjoringer.hentEksportfil(filId);
        byte[] innhold = arkiv.hent(fil.blobUrl());
        return ResponseEntity.ok()
                .contentType(mediatype(fil.type()))
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(fil.filnavn()).build().toString())
                .body(innhold);
    }

    private static MediaType mediatype(String type) {
        return switch (type) {
            case "PDF" -> MediaType.valueOf("application/zip");
            case "CSV" -> MediaType.valueOf("text/csv");
            case "XLSX" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.TEXT_PLAIN;
        };
    }

    @PostMapping("/{id}/godkjenn")
    String godkjenn(@PathVariable Long id) {
        kjoringer.godkjenn(id);
        return "redirect:/kjoringer/" + id;
    }

    @PostMapping("/{id}/forkast")
    String forkast(@PathVariable Long id) {
        kjoringer.forkast(id);
        return "redirect:/kjoringer/" + id;
    }

    @GetMapping("/{id}/faktura/{fakturaId}")
    String faktura(@PathVariable Long id, @PathVariable Long fakturaId, Model model) {
        var produktNavn = produkter.alle().stream()
                .collect(Collectors.toMap(p -> p.id(), p -> p.navn()));
        model.addAttribute("kjoring", kjoringer.hent(id));
        model.addAttribute("faktura", kjoringer.hentFaktura(fakturaId));
        model.addAttribute("linjer", kjoringer.linjer(fakturaId));
        model.addAttribute("funn", kjoringer.funnForFaktura(fakturaId));
        model.addAttribute("produktNavn", produktNavn);
        return "kjoring/faktura";
    }
}
