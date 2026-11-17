package no.digdir.forsystem.web.hjelp;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import no.digdir.forsystem.billing.Kontrollkode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Læringssenter (specs/016): the start page and the article pages. Read-only; every signed-in user may read.
 * Content comes from {@link Hjelpebibliotek}, which is loaded and checked at startup.
 */
@Controller
class HjelpController {

    private static final DateTimeFormatter DATO = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.forLanguageTag("nb"));

    /** A start-page card. */
    record Kort(String tittel, String tekst, String url) {}

    /** A «Jeg vil …» entry with the article's address. */
    record JegVilLenke(String tekst, String url) {}

    private final Hjelpebibliotek bibliotek;
    private final Hjelpesok sok;
    private final Map<String, String> titler = new LinkedHashMap<>();

    HjelpController(Hjelpebibliotek bibliotek, Hjelpesok sok) {
        this.bibliotek = bibliotek;
        this.sok = sok;
        bibliotek.alle().forEach(a -> titler.put(a.id(), a.tittel()));
    }

    @GetMapping("/hjelp")
    String start(Model model) {
        model.addAttribute("seksjon", "hjelp");
        model.addAttribute("meny", bibliotek.meny());
        model.addAttribute("titler", titler);
        model.addAttribute("gjeldende", "");
        model.addAttribute("kort", kort());
        model.addAttribute("jegVil", bibliotek.jegVil().stream()
                .map(j -> new JegVilLenke(j.tekst(), "/hjelp/" + j.artikkelId())).toList());
        return "hjelp/start";
    }

    /** Search (FR-022). An empty query goes back to the start page. */
    @GetMapping("/hjelp/sok")
    String sok(@RequestParam(name = "q", required = false) String q, Model model) {
        if (q == null || q.isBlank()) {
            return "redirect:/hjelp";
        }
        model.addAttribute("seksjon", "hjelp");
        model.addAttribute("meny", bibliotek.meny());
        model.addAttribute("titler", titler);
        model.addAttribute("gjeldende", "");
        model.addAttribute("q", q.strip());
        var seksjonTitler = new LinkedHashMap<String, String>();
        bibliotek.meny().seksjoner().forEach(s -> seksjonTitler.put(s.id(), s.tittel()));
        model.addAttribute("seksjonTitler", seksjonTitler);
        model.addAttribute("treff", sok.sok(q));
        return "hjelp/sok";
    }

    @GetMapping("/hjelp/{seksjon}/{side}")
    String artikkel(@PathVariable String seksjon, @PathVariable String side, Model model) {
        Artikkel a = bibliotek.artikkel(seksjon, side)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("seksjon", "hjelp");
        model.addAttribute("meny", bibliotek.meny());
        model.addAttribute("titler", titler);
        model.addAttribute("gjeldende", a.id());
        model.addAttribute("artikkel", a);
        model.addAttribute("seksjonTittel", bibliotek.meny().seksjoner().stream()
                .filter(s -> s.id().equals(a.seksjon())).findFirst().map(Meny.Seksjon::tittel).orElse(a.seksjon()));
        model.addAttribute("oppdatertTekst", DATO.format(a.oppdatert()));
        model.addAttribute("kontrollkoder", Kontrollkode.values());
        model.addAttribute("kontrollfunn", bibliotek.kontrollfunn());
        return "hjelp/artikkel";
    }

    private List<Kort> kort() {
        return List.of(
                new Kort("Ny her? Start her", "Hva forsystem er, rollene, og en rundtur i skjermbildene.",
                        url("kom-i-gang")),
                new Kort("Månedsrutinen steg for steg", "Fra bruksdata til LG04-fil i seks steg.",
                        url("manedsrutinen")),
                new Kort("Noe gikk galt", "Finn det du ser, og hva du gjør med det.", url("nar-noe-gar-galt")),
                new Kort("Oppslag", "Kontrollfunn, statuser, filer og ordliste.", "/hjelp/oppslag/kontrollfunn"));
    }

    private String url(String seksjon) {
        return bibliotek.forsteI(seksjon).map(Artikkel::url).orElse("/hjelp");
    }
}
