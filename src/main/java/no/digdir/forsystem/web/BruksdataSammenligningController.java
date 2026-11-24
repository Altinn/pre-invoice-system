package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.ProduktRepository;
import no.digdir.forsystem.usage.BruksdataEndring;
import no.digdir.forsystem.usage.BruksdataKandidatService;
import no.digdir.forsystem.usage.Kundeavvik;
import no.digdir.forsystem.usage.Kundeavvikskategori;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The comparison of a correction candidate, grouped by customer (specs/007, specs/019). */
@Controller
@RequestMapping("/bruksdata/sammenligninger")
class BruksdataSammenligningController {

    /** Customers per page; a page never splits one customer's products (FR-008). */
    private static final int SIDE_STORRELSE = 50;

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
               @RequestParam(required = false) String kunde,
               @RequestParam(required = false) String kundenummer,
               @RequestParam(required = false) Long produktId,
               @RequestParam(required = false) String kategori,
               @RequestParam(required = false) String fortegn,
               @RequestParam(defaultValue = "1") int side,
               Model model) {
        var resultat = kandidater.hent(id);
        var grunnlag = kjoringer.hent(resultat.sammenligning().grunnlagKjoringId());
        Map<Long, String> produktnavn = new LinkedHashMap<>();
        var produktliste = produkter.findAllByOrderByKode();
        produktliste.forEach(p -> produktnavn.put(p.id(), p.navn()));
        Map<Long, BigDecimal> enhetspriser = kjoringer.enhetspriser(grunnlag.id());
        Map<Long, List<BruksdataEndring>> perKunde = resultat.endringerPerKunde();

        List<KundeRad> rader = resultat.kundeavvik().stream()
                .map(a -> KundeRad.av(a, perKunde.getOrDefault(a.id(), List.of()), enhetspriser))
                .toList();

        String kundeFilter = kunde == null ? "" : kunde.strip().toLowerCase(Locale.ROOT);
        String kundenummerFilter = kundenummer == null ? "" : kundenummer.strip();
        Kundeavvikskategori kategoriFilter = kategori == null || kategori.isBlank()
                ? null : Kundeavvikskategori.valueOf(kategori);
        List<KundeRad> filtrert = rader.stream()
                .filter(r -> kundeFilter.isEmpty()
                        || r.avvik().organisasjonsnummer().contains(kundeFilter)
                        || r.avvik().navn().toLowerCase(Locale.ROOT).contains(kundeFilter))
                .filter(r -> kundenummerFilter.isEmpty()
                        || (r.avvik().kundenummer() != null && r.avvik().kundenummer().contains(kundenummerFilter)))
                .filter(r -> produktId == null || r.endringer().stream().anyMatch(e -> produktId.equals(e.produktId())))
                .filter(r -> kategoriFilter == null || r.avvik().har(kategoriFilter))
                .filter(r -> fortegn == null || fortegn.isBlank()
                        || ("POSITIV".equals(fortegn) && r.fortegn() > 0)
                        || ("NEGATIV".equals(fortegn) && r.fortegn() < 0))
                .toList();
        int antallSider = Math.max(1, (filtrert.size() + SIDE_STORRELSE - 1) / SIDE_STORRELSE);
        int gyldigSide = Math.max(1, Math.min(side, antallSider));
        int fra = Math.min((gyldigSide - 1) * SIDE_STORRELSE, filtrert.size());
        int til = Math.min(fra + SIDE_STORRELSE, filtrert.size());

        // Category text → number of customers, in category order (FR-007).
        Map<String, Long> perKategori = new LinkedHashMap<>();
        for (Kundeavvikskategori k : Kundeavvikskategori.values()) {
            long antall = rader.stream().filter(r -> r.avvik().har(k)).count();
            if (antall > 0) {
                perKategori.put(k.tekst(), antall);
            }
        }
        List<KundeRad> velgbare = rader.stream().filter(KundeRad::kanVelges).toList();
        BigDecimal velgbartBelop = velgbare.stream().map(KundeRad::belop).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("resultat", resultat);
        model.addAttribute("sammenligning", resultat.sammenligning());
        model.addAttribute("kandidat", resultat.kandidat());
        model.addAttribute("kunder", filtrert.subList(fra, til));
        model.addAttribute("antallKunder", rader.size());
        model.addAttribute("antallFiltrert", filtrert.size());
        model.addAttribute("antallEndringer", resultat.endringer().size());
        model.addAttribute("perKategori", perKategori);
        model.addAttribute("antallVelgbare", velgbare.size());
        model.addAttribute("velgbartBelop", velgbartBelop);
        model.addAttribute("kategorier", Kundeavvikskategori.values());
        model.addAttribute("side", gyldigSide);
        model.addAttribute("antallSider", antallSider);
        model.addAttribute("kunde", kunde == null ? "" : kunde.strip());
        model.addAttribute("kundenummer", kundenummerFilter);
        model.addAttribute("valgtProduktId", produktId);
        model.addAttribute("valgtKategori", kategori == null ? "" : kategori);
        model.addAttribute("valgtFortegn", fortegn == null ? "" : fortegn);
        model.addAttribute("grunnlag", grunnlag);
        model.addAttribute("produktnavn", produktnavn);
        model.addAttribute("produkter", produktliste);
        return "bruksdata/sammenligning";
    }

    @PostMapping("/{id}/kjoring")
    String opprettKjoring(@PathVariable Long id,
                          @RequestParam(required = false) List<String> orgnr,
                          @RequestParam(defaultValue = "false") boolean alle,
                          @RequestParam String aarsak,
                          @RequestParam Long grunnlagKjoringId) {
        var resultat = kandidater.hent(id);
        if (!resultat.sammenligning().grunnlagKjoringId().equals(grunnlagKjoringId)) {
            throw new Regelbrudd("Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt.");
        }
        var kjoring = kjoringer.genererKorrigeringForKunder(id, orgnr, alle, aarsak);
        return "redirect:/kjoringer/" + kjoring.id();
    }
}
