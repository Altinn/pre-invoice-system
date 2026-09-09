package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import no.digdir.forsystem.registry.Pris;
import no.digdir.forsystem.registry.PrisversjonService;
import no.digdir.forsystem.registry.ProduktService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/prisversjoner")
class PrisversjonController {

    private final PrisversjonService prisversjoner;
    private final ProduktService produkter;

    PrisversjonController(PrisversjonService prisversjoner, ProduktService produkter) {
        this.prisversjoner = prisversjoner;
        this.produkter = produkter;
    }

    @GetMapping
    String liste(Model model) {
        model.addAttribute("prisversjoner", prisversjoner.alle());
        return "prisversjon/liste";
    }

    @GetMapping("/{id}")
    String detalj(@PathVariable Long id, Model model) {
        var versjon = prisversjoner.hent(id);
        Map<Long, BigDecimal> prisPerProdukt = new LinkedHashMap<>();
        for (Pris p : prisversjoner.priser(id)) {
            prisPerProdukt.put(p.produktId(), p.enhetspris());
        }
        model.addAttribute("versjon", versjon);
        model.addAttribute("produkter", produkter.alle());
        model.addAttribute("prisPerProdukt", prisPerProdukt);
        return "prisversjon/detalj";
    }

    @PostMapping
    String opprett(@RequestParam String navn,
                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gyldigFra,
                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gyldigTil) {
        var lagret = prisversjoner.opprettUtkast(navn.trim(), gyldigFra, gyldigTil);
        return "redirect:/prisversjoner/" + lagret.id();
    }

    @PostMapping("/{id}/pris")
    String settPris(@PathVariable Long id, @RequestParam Long produktId, @RequestParam BigDecimal enhetspris) {
        prisversjoner.settPris(id, produktId, enhetspris);
        return "redirect:/prisversjoner/" + id;
    }

    @PostMapping("/{id}/aktiver")
    String aktiver(@PathVariable Long id) {
        prisversjoner.aktiver(id);
        return "redirect:/prisversjoner/" + id;
    }

    @PostMapping("/{id}/arkiver")
    String arkiver(@PathVariable Long id) {
        prisversjoner.arkiver(id);
        return "redirect:/prisversjoner/" + id;
    }
}
