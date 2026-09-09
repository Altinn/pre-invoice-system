package no.digdir.forsystem.web;

import no.digdir.forsystem.common.HendelsesloggRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Read-only audit page: the 200 most recent state changes. */
@Controller
class HendelsesloggController {

    private final HendelsesloggRepository hendelser;

    HendelsesloggController(HendelsesloggRepository hendelser) {
        this.hendelser = hendelser;
    }

    @GetMapping("/hendelseslogg")
    String liste(Model model) {
        model.addAttribute("hendelser", hendelser.findTop200ByOrderByTidspunktDescIdDesc());
        return "hendelseslogg/liste";
    }
}
