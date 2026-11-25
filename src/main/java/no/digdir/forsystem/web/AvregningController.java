package no.digdir.forsystem.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.billing.AvregningService;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The Avregning tab (specs/020): tick months, enter a price adjustment per product, generate a draft run. */
@Controller
@RequestMapping("/avregning")
class AvregningController {

    private static final String JUSTERING_PREFIKS = "justering-";

    private final AvregningService avregning;

    AvregningController(AvregningService avregning) {
        this.avregning = avregning;
    }

    @GetMapping
    String side(@RequestParam(required = false) Integer aar, Model model) {
        int valgtAar = aar != null ? aar : avregning.standardAar();
        model.addAttribute("aar", valgtAar);
        model.addAttribute("maaneder", avregning.maaneder(valgtAar));
        model.addAttribute("produkter", avregning.produkter());
        model.addAttribute("avregninger", avregning.avregninger());
        model.addAttribute("unntattKode", AvregningService.UNNTATT_PRODUKTKODE);
        return "avregning/side";
    }

    @PostMapping
    String generer(@RequestParam int aar, @RequestParam(required = false) List<String> maaned,
                   @RequestParam Map<String, String> parametre) {
        List<Periode> maaneder = (maaned == null ? List.<String>of() : maaned).stream()
                .map(AvregningController::periode).toList();
        Map<Long, BigDecimal> justeringer = new LinkedHashMap<>();
        parametre.forEach((navn, verdi) -> {
            if (navn.startsWith(JUSTERING_PREFIKS) && verdi != null && !verdi.isBlank()) {
                justeringer.put(produktId(navn), tall(verdi));
            }
        });
        Fakturakjoring kjoring = avregning.generer(aar, maaneder, justeringer);
        return "redirect:/kjoringer/" + kjoring.id();
    }

    private static Periode periode(String tekst) {
        try {
            return Periode.parse(tekst);
        } catch (RuntimeException e) {
            throw new Regelbrudd("Ugyldig måned: " + tekst + " (forventet ÅÅÅÅ-MM)");
        }
    }

    private static Long produktId(String parameter) {
        try {
            return Long.valueOf(parameter.substring(JUSTERING_PREFIKS.length()));
        } catch (NumberFormatException e) {
            throw new Regelbrudd("Ugyldig produkt: " + parameter);
        }
    }

    /** Accepts «0,25» and «0.25». */
    private static BigDecimal tall(String verdi) {
        try {
            return new BigDecimal(verdi.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new Regelbrudd("Prisjusteringen «" + verdi + "» er ikke et tall");
        }
    }
}
