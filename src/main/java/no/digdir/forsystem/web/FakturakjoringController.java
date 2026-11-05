package no.digdir.forsystem.web;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import no.digdir.forsystem.billing.BruksdataavstemmingService;
import no.digdir.forsystem.billing.Faktura;
import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.billing.Fakturatekster;
import no.digdir.forsystem.billing.FakturakjoringService;
import no.digdir.forsystem.billing.Kontrollfunn;
import no.digdir.forsystem.billing.Kontrollkode;
import no.digdir.forsystem.billing.NullstillingService;
import no.digdir.forsystem.common.HendelsesloggRepository;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.export.Lg04Avvik;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/kjoringer")
class FakturakjoringController {

    /** Audit-trail entity name of a run (FakturakjoringService, EksportService). */
    private static final String ENTITET = "FAKTURAKJORING";

    private final FakturakjoringService kjoringer;
    private final ProduktService produkter;
    private final EksportService eksport;
    private final BruksdataavstemmingService avstemming;
    private final HendelsesloggRepository hendelser;
    /** Present only in test environments (forsystem.testverktoy.enabled). */
    private final Optional<NullstillingService> nullstilling;

    FakturakjoringController(FakturakjoringService kjoringer, ProduktService produkter,
                             EksportService eksport, BruksdataavstemmingService avstemming,
                             HendelsesloggRepository hendelser, Optional<NullstillingService> nullstilling) {
        this.kjoringer = kjoringer;
        this.produkter = produkter;
        this.eksport = eksport;
        this.avstemming = avstemming;
        this.hendelser = hendelser;
        this.nullstilling = nullstilling;
    }

    @GetMapping
    String liste(Model model) {
        // Size and problems per run, so the list says which run needs attention (specs/006 FR-012).
        model.addAttribute("kjoringer", kjoringer.alle().stream().map(k -> {
            var sammendrag = kjoringer.sammendrag(k.id());
            var status = Funnstatus.av(kjoringer.funn(k.id()));
            return new Kjoringsrad(k, sammendrag.antallFakturaer(), sammendrag.sumBelop(),
                    status.antallBlokkerende(), status.antallAapne(), status.antallAksepterte());
        }).toList());
        nullstilling.ifPresent(n -> model.addAttribute("nullstillbare", n.perioderMedData()));
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

    /** Oversikt (default tab): progress, the next step and the summary (specs/005, specs/006). */
    @GetMapping("/{id}")
    String detalj(@PathVariable Long id, Model model) {
        var fane = fane(id, model);
        var kjoring = fane.kjoring();
        model.addAttribute("fremdrift", Fremdrift.av(kjoring,
                hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc(ENTITET, id)));
        // The usage check matters only before approval; other statuses skip the query.
        model.addAttribute("bruksdataavstemming",
                "GENERERT".equals(kjoring.status()) && !kjoring.erKorrigering()
                        ? avstemming.forKjoring(id) : null);
        model.addAttribute("antallValgteEndringer",
                kjoring.erKorrigering() ? kjoringer.valgteEndringIder(id).size() : 0);
        model.addAttribute("sammendrag", kjoringer.sammendrag(id));
        model.addAttribute("antallOrdrer", eksport.forventetAntallOrdrer(id));
        model.addAttribute("lg04Avstemming", eksport.avstemmingFor(id).orElse(null));
        return "kjoring/detalj";
    }

    /** Kontroller: findings per code, the usage reconciliation and the LG04 result (specs/006 FR-007). */
    @GetMapping("/{id}/kontroller")
    String kontroller(@PathVariable Long id, Model model) {
        var fane = fane(id, model);
        model.addAttribute("funngrupper", Funngruppe.av(fane.funn()));
        model.addAttribute("kontrollkoder", Kontrollkode.values());
        model.addAttribute("avstemming", fane.kjoring().erKorrigering() ? null : avstemming.forKjoring(id));
        model.addAttribute("antallValgteEndringer", fane.kjoring().erKorrigering()
                ? kjoringer.valgteEndringIder(id).size() : 0);
        model.addAttribute("lg04Avstemming", eksport.avstemmingFor(id).orElse(null));
        model.addAttribute("utenFakturareferanse", kjoringer.utenFakturareferanse(id));
        return "kjoring/kontroller";
    }

    /**
     * Tick or untick «Funn akseptert» on one warning (specs/013). An unticked checkbox sends no
     * {@code akseptert}, which reads as false.
     */
    @PostMapping("/{id}/kontrollfunn/{funnId}/aksept")
    String aksept(@PathVariable Long id, @PathVariable Long funnId,
                  @RequestParam(defaultValue = "false") boolean akseptert) {
        kjoringer.settAksept(id, funnId, akseptert);
        return "redirect:/kjoringer/" + id + "/kontroller#funn-" + funnId;
    }

    /** Tick or untick «Funn akseptert» on a group row: every warning of the code in the run (specs/013). */
    @PostMapping("/{id}/kontrollfunn/aksept")
    String akseptForKode(@PathVariable Long id, @RequestParam String kode,
                         @RequestParam(defaultValue = "false") boolean akseptert) {
        kjoringer.settAkseptForKode(id, kode, akseptert);
        return "redirect:/kjoringer/" + id + "/kontroller#gruppe-" + kode;
    }

    /** The usage reconciliation moved into the Kontroller tab; old links land there (specs/006 FR-004). */
    @GetMapping("/{id}/avstemming")
    String avstemming(@PathVariable Long id) {
        return "redirect:/kjoringer/" + id + "/kontroller#bruksdata";
    }

    /** Fakturaer: search, sort and paging (specs/006 FR-008/009). */
    @GetMapping("/{id}/fakturaer")
    String fakturaer(@PathVariable Long id, @RequestParam(required = false) String q,
                     @RequestParam(required = false) String sorter,
                     @RequestParam(required = false) Integer side, Model model) {
        var fane = fane(id, model);
        model.addAttribute("utvalg", Fakturautvalg.av(fane.fakturaer(), kjoringer.kundenavn(fane.fakturaer()),
                q, sorter, side));
        model.addAttribute("underTerskel", kjoringer.underTerskel(id));
        return "kjoring/fakturaer";
    }

    @GetMapping("/{id}/filer")
    String filer(@PathVariable Long id, Model model) {
        fane(id, model);
        model.addAttribute("eksportfiler", kjoringer.eksportfiler(id));
        model.addAttribute("lg04Avstemming", eksport.avstemmingFor(id).orElse(null));
        return "kjoring/filer";
    }

    /** Historikk: the run's own audit trail, newest first (specs/006 FR-011). */
    @GetMapping("/{id}/historikk")
    String historikk(@PathVariable Long id, Model model) {
        fane(id, model);
        model.addAttribute("hendelser", hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc(ENTITET, id));
        return "kjoring/historikk";
    }

    private record Fane(Fakturakjoring kjoring, List<Faktura> fakturaer, List<Kontrollfunn> funn) {
    }

    /**
     * The run and the counts every tab's header shows: invoices, blocking findings, and warnings not yet
     * accepted (specs/013) — an accepted warning no longer counts on the Kontroller badge.
     */
    private Fane fane(Long id, Model model) {
        var kjoring = kjoringer.hent(id);
        var fakturaer = kjoringer.fakturaer(id);
        var funn = kjoringer.funn(id);
        var status = Funnstatus.av(funn);
        model.addAttribute("kjoring", kjoring);
        model.addAttribute("antallFakturaer", fakturaer.size());
        model.addAttribute("funnstatus", status);
        model.addAttribute("antallBlokkerende", status.antallBlokkerende());
        model.addAttribute("antallAdvarsler", status.antallAapne());
        return new Fane(kjoring, fakturaer, funn);
    }

    /**
     * A stopped export (LG04 did not reconcile) comes back to the run page with the explanation, not
     * to the generic error page; it is logged here because the export's own transaction rolled back.
     */
    @PostMapping("/{id}/eksporter")
    String eksporter(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            eksport.eksporter(id);
        } catch (Lg04Avvik avvik) {
            eksport.loggStoppetEksport(id, avvik);
            redirect.addFlashAttribute("eksportStoppet", avvik.getMessage());
        }
        return "redirect:/kjoringer/" + id;
    }

    /**
     * Save the run's invoice texts (specs/009). A refusal — invalid text or a locked run — comes back to
     * the run page with the message and the submitted values, so nothing typed is lost.
     */
    @PostMapping("/{id}/tekster")
    String tekster(@PathVariable Long id, @RequestParam(defaultValue = "") String topptekst,
                   @RequestParam(defaultValue = "") String bunntekst,
                   @RequestParam(defaultValue = "") String varReferanse, RedirectAttributes redirect) {
        var innsendt = new Fakturatekster(topptekst, bunntekst, varReferanse);
        try {
            kjoringer.endreTekster(id, innsendt);
            redirect.addFlashAttribute("teksterLagret", true);
        } catch (Regelbrudd e) {
            redirect.addFlashAttribute("teksterFeil", e.getMessage());
            redirect.addFlashAttribute("innsendteTekster", innsendt);
        }
        return "redirect:/kjoringer/" + id + "#tekster";
    }

    /** The fakturagrunnlag as a spreadsheet, also before approval (specs/002 US3). */
    @GetMapping("/{id}/fakturagrunnlag.{format:csv|xlsx}")
    ResponseEntity<byte[]> fakturagrunnlag(@PathVariable Long id, @PathVariable String format) {
        EksportService.Nedlasting n = eksport.regneark(id, format);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(n.mediatype()))
                .header("Content-Disposition", ContentDisposition.attachment().filename(n.filnavn()).build().toString())
                .body(n.innhold());
    }

    /**
     * The invoice list, the would-be invoices left out under the threshold (specs/011), the invoices without
     * fakturareferanse (specs/012), or a list from the Kontroller tab (specs/013), as a spreadsheet.
     */
    @GetMapping("/{id}/{liste:fakturaliste|under-terskel|uten-fakturareferanse|kontrollfunn|avstemming-bruksdata|ikke-fakturert}.{format:csv|xlsx}")
    ResponseEntity<byte[]> liste(@PathVariable Long id, @PathVariable String liste, @PathVariable String format) {
        EksportService.Nedlasting n = eksport.liste(id, liste, format);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(n.mediatype()))
                .header("Content-Disposition", ContentDisposition.attachment().filename(n.filnavn()).build().toString())
                .body(n.innhold());
    }

    @GetMapping("/{id}/lg04-kontroll")
    String lg04Kontroll(@PathVariable Long id, Model model) {
        model.addAttribute("kjoring", kjoringer.hent(id));
        model.addAttribute("kontroll", eksport.kontrollerArkiv(id));
        return "kjoring/lg04-kontroll";
    }

    @GetMapping("/{id}/eksportfil/{filId}")
    ResponseEntity<byte[]> lastNed(@PathVariable Long id, @PathVariable Long filId) {
        EksportService.Nedlasting n = eksport.lastNedEksportfil(id, filId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(n.mediatype()))
                .header("Content-Disposition", ContentDisposition.attachment().filename(n.filnavn()).build().toString())
                .body(n.innhold());
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
        var linjer = kjoringer.linjer(fakturaId);
        model.addAttribute("linjer", linjer);
        // The reference once, in the header; several only when the invoice is split (DELT_REFERANSE).
        model.addAttribute("referanser", linjer.stream()
                .map(l -> Stream.of(l.fakturareferanse(), l.bestillingsnummer())
                        .filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(" / ")))
                .map(r -> r.isEmpty() ? "ingen referanse" : r).distinct().toList());
        model.addAttribute("funn", kjoringer.funnForFaktura(fakturaId));
        model.addAttribute("produktNavn", produktNavn);
        return "kjoring/faktura";
    }
}
