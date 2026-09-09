package no.digdir.forsystem.web;

import no.digdir.forsystem.registry.ProduktService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/produkter")
class ProduktController {

    private final ProduktService produkter;

    ProduktController(ProduktService produkter) {
        this.produkter = produkter;
    }

    @GetMapping
    String liste(Model model) {
        model.addAttribute("produkter", produkter.alle());
        return "produkt/liste";
    }

    @GetMapping("/{id}")
    String rediger(@PathVariable Long id, Model model) {
        model.addAttribute("produkt", produkter.hent(id));
        return "produkt/rediger";
    }

    @PostMapping
    String opprett(@RequestParam String kode, @RequestParam String navn,
                   @RequestParam(required = false) String enhet) {
        produkter.opprett(kode.trim(), navn.trim(), enhet);
        return "redirect:/produkter";
    }

    @PostMapping("/{id}")
    String oppdater(@PathVariable Long id,
                    @RequestParam String navn,
                    @RequestParam(required = false) Integer artikkelId,
                    @RequestParam(required = false) String konto,
                    @RequestParam(required = false) String dim1,
                    @RequestParam(required = false) String dim2,
                    @RequestParam(required = false) String dim4,
                    @RequestParam(required = false) String enhet,
                    @RequestParam(defaultValue = "false") boolean aktiv) {
        produkter.oppdater(id, navn, artikkelId, konto, dim1, dim2, dim4, enhet, aktiv);
        return "redirect:/produkter";
    }
}
