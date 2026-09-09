package no.digdir.forsystem.web;

import no.digdir.forsystem.registry.Avtalestatus;
import no.digdir.forsystem.registry.KundeService;
import no.digdir.forsystem.registry.ProduktService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class KundeController {

    private final KundeService kunder;
    private final ProduktService produkter;

    KundeController(KundeService kunder, ProduktService produkter) {
        this.kunder = kunder;
        this.produkter = produkter;
    }

    @GetMapping("/kunder")
    String liste(Model model) {
        model.addAttribute("kunder", kunder.alle());
        return "kunde/liste";
    }

    @GetMapping("/kunder/ny")
    String nyttSkjema(Model model) {
        model.addAttribute("avtalestatuser", Avtalestatus.values());
        return "kunde/ny";
    }

    @PostMapping("/kunder")
    String opprett(@RequestParam String organisasjonsnummer,
                   @RequestParam String virksomhetsnavn,
                   @RequestParam(required = false) String fakturamottakerOrgnr,
                   @RequestParam Avtalestatus avtalestatus,
                   RedirectAttributes flash) {
        String orgnr = organisasjonsnummer.trim();
        String navn = virksomhetsnavn.trim();
        var lagret = kunder.opprett(orgnr, navn, fakturamottakerOrgnr, avtalestatus);
        brregAdvarsel(orgnr, navn, flash);
        return "redirect:/kunder/" + lagret.id();
    }

    @GetMapping("/kunder/{id}")
    String detalj(@PathVariable Long id, Model model) {
        var produktListe = produkter.alle();
        var produktNavn = produktListe.stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.id(), p -> p.navn()));
        model.addAttribute("kunde", kunder.hent(id));
        model.addAttribute("referanser", kunder.referanser(id));
        model.addAttribute("regler", kunder.regler(id));
        model.addAttribute("produkter", produktListe);
        model.addAttribute("produktNavn", produktNavn);
        model.addAttribute("avtalestatuser", Avtalestatus.values());
        return "kunde/detalj";
    }

    @PostMapping("/kunder/{id}")
    String oppdater(@PathVariable Long id,
                    @RequestParam String virksomhetsnavn,
                    @RequestParam(required = false) String fakturamottakerOrgnr,
                    @RequestParam Avtalestatus avtalestatus,
                    RedirectAttributes flash) {
        String navn = virksomhetsnavn.trim();
        kunder.oppdater(id, navn, fakturamottakerOrgnr, avtalestatus);
        brregAdvarsel(kunder.hent(id).organisasjonsnummer(), navn, flash);
        return "redirect:/kunder/" + id;
    }

    /** Non-blocking BRREG name-mismatch warning (K-16), surfaced as a flash message. */
    private void brregAdvarsel(String orgnr, String navn, RedirectAttributes flash) {
        kunder.brregNavnAdvarsel(orgnr, navn).ifPresent(offisielt -> flash.addFlashAttribute("advarsel",
                "Virksomhetsnavnet avviker fra Enhetsregisteret (BRREG): «" + offisielt + "»"));
    }

    @PostMapping("/kunder/{id}/referanser")
    String leggTilReferanse(@PathVariable Long id,
                            @RequestParam(required = false) Long produktId,
                            @RequestParam(required = false) String fakturareferanse,
                            @RequestParam(required = false) String bestillingsnummer) {
        kunder.leggTilReferanse(id, produktId, fakturareferanse, bestillingsnummer);
        return "redirect:/kunder/" + id;
    }

    @PostMapping("/referanser/{referanseId}/slett")
    String slettReferanse(@PathVariable Long referanseId, @RequestParam Long kundeId) {
        kunder.slettReferanse(referanseId);
        return "redirect:/kunder/" + kundeId;
    }

    @PostMapping("/kunder/{id}/regler")
    String leggTilRegel(@PathVariable Long id,
                        @RequestParam String kundenummer,
                        @RequestParam(required = false) Long produktId,
                        @RequestParam(required = false) String servicekode,
                        @RequestParam(required = false) String tilleggstekst) {
        kunder.leggTilRegel(id, kundenummer.trim(), produktId, servicekode, tilleggstekst);
        return "redirect:/kunder/" + id;
    }

    @PostMapping("/regler/{regelId}/slett")
    String slettRegel(@PathVariable Long regelId, @RequestParam Long kundeId) {
        kunder.slettRegel(regelId);
        return "redirect:/kunder/" + kundeId;
    }
}
