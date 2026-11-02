package no.digdir.forsystem.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.BruksdataEndring;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/bruksdata/sammenligninger")
class BruksdataSammenligningController {

    private static final int SIDE_STORRELSE = 100;

    private final BruksdataKandidatService kandidater;
    private final FakturakjoringService kjoringer;
    private final ProduktRepository produkter;

    BruksdataSammenligningController(BruksdataKandidatService kandidater,
                                     FakturakjoringService kjoringer,
                                     ProduktRepository produkter) {
        this.kandidater = kandidater;
        this.kjoringer = kjoringer;
        this.produkter = produkter;
    }

    @GetMapping("/{id}")
    String vis(@PathVariable Long id,
               @RequestParam(required = false) String orgnr,
               @RequestParam(required = false) Long produktId,
               @RequestParam(required = false) String endringstype,
               @RequestParam(required = false) String fortegn,
               @RequestParam(defaultValue = "1") int side,
               Model model) {
        var resultat = kandidater.hent(id);
        Map<Long, String> produktnavn = new LinkedHashMap<>();
        var produktliste = produkter.findAllByOrderByKode();
        produktliste.forEach(p -> produktnavn.put(p.id(), p.navn()));
        String orgnrFilter = orgnr == null ? "" : orgnr.trim();
        List<BruksdataEndring> filtrert = resultat.endringer().stream()
                .filter(e -> orgnrFilter.isEmpty() || e.organisasjonsnummer().contains(orgnrFilter))
                .filter(e -> produktId == null || produktId.equals(e.produktId()))
                .filter(e -> endringstype == null || endringstype.isBlank()
                        || endringstype.equals(e.endringstype()))
                .filter(e -> fortegn == null || fortegn.isBlank()
                        || ("POSITIV".equals(fortegn) && e.delta().signum() > 0)
                        || ("NEGATIV".equals(fortegn) && e.delta().signum() < 0))
                .toList();
        int antallSider = Math.max(1, (filtrert.size() + SIDE_STORRELSE - 1) / SIDE_STORRELSE);
        int gyldigSide = Math.max(1, Math.min(side, antallSider));
        int fra = Math.min((gyldigSide - 1) * SIDE_STORRELSE, filtrert.size());
        int til = Math.min(fra + SIDE_STORRELSE, filtrert.size());
        model.addAttribute("resultat", resultat);
        model.addAttribute("sammenligning", resultat.sammenligning());
        model.addAttribute("kandidat", resultat.kandidat());
        model.addAttribute("endringer", filtrert.subList(fra, til));
        model.addAttribute("antallEndringer", resultat.endringer().size());
        model.addAttribute("antallFiltrert", filtrert.size());
        model.addAttribute("side", gyldigSide);
        model.addAttribute("antallSider", antallSider);
        model.addAttribute("orgnr", orgnrFilter);
        model.addAttribute("valgtProduktId", produktId);
        model.addAttribute("valgtEndringstype", endringstype == null ? "" : endringstype);
        model.addAttribute("valgtFortegn", fortegn == null ? "" : fortegn);
        model.addAttribute("grunnlag", kjoringer.hent(resultat.sammenligning().grunnlagKjoringId()));
        model.addAttribute("produktnavn", produktnavn);
        model.addAttribute("produkter", produktliste);
        return "bruksdata/sammenligning";
    }

    @PostMapping("/{id}/kjoring")
    String opprettKjoring(@PathVariable Long id,
                          @RequestParam(required = false) java.util.List<Long> endringId,
                          @RequestParam String aarsak,
                          @RequestParam Long grunnlagKjoringId) {
        var resultat = kandidater.hent(id);
        if (!resultat.sammenligning().grunnlagKjoringId().equals(grunnlagKjoringId)) {
            throw new no.digdir.forsystem.common.Regelbrudd(
                    "Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt.");
        }
        var kjoring = kjoringer.genererKorrigering(id, endringId, aarsak);
        return "redirect:/kjoringer/" + kjoring.id();
    }
}
