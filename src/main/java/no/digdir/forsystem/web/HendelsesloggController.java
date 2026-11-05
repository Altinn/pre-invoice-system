package no.digdir.forsystem.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import no.digdir.forsystem.common.Detaljtekst;
import no.digdir.forsystem.common.Hendelsesfilter;
import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.common.HendelsesloggSok;
import no.digdir.forsystem.export.EksportService;
import no.digdir.forsystem.export.HendelsesloggEksport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only audit page (specs/014): the whole log, 50 rows a page, newest first, filtered by date range, user,
 * entity and action, and downloadable as CSV/XLSX with the same filters.
 */
@Controller
class HendelsesloggController {

    private static final DateTimeFormatter VISNING = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final HendelsesloggSok sok;
    private final HendelsesloggEksport eksport;

    HendelsesloggController(HendelsesloggSok sok, HendelsesloggEksport eksport) {
        this.sok = sok;
        this.eksport = eksport;
    }

    /** A log row as the page shows it: Norwegian time and readable details. */
    record Rad(Hendelseslogg hendelse, String tidspunkt, String detaljer) {
        static Rad av(Hendelseslogg h) {
            return new Rad(h, VISNING.format(h.tidspunkt().atZoneSameInstant(Hendelsesfilter.OSLO)),
                    Detaljtekst.les(h.detaljer()));
        }
    }

    @GetMapping("/hendelseslogg")
    String liste(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fra,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate til,
                 @RequestParam(required = false) String bruker,
                 @RequestParam(required = false) String entitet,
                 @RequestParam(required = false) String handling,
                 @RequestParam(defaultValue = "1") int side, Model model) {
        var filter = new Hendelsesfilter(fra, til, bruker, entitet, handling);
        var resultat = sok.side(filter, side);
        model.addAttribute("filter", filter);
        model.addAttribute("resultat", resultat);
        model.addAttribute("rader", resultat.rader().stream().map(Rad::av).toList());
        model.addAttribute("entiteter", sok.entiteter());
        model.addAttribute("handlinger", sok.handlinger());
        return "hendelseslogg/liste";
    }

    @GetMapping("/hendelseslogg.{format:csv|xlsx}")
    ResponseEntity<byte[]> lastNed(@PathVariable String format,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fra,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate til,
                                   @RequestParam(required = false) String bruker,
                                   @RequestParam(required = false) String entitet,
                                   @RequestParam(required = false) String handling) {
        EksportService.Nedlasting n = eksport.lastNed(new Hendelsesfilter(fra, til, bruker, entitet, handling),
                format, LocalDate.now(Hendelsesfilter.OSLO));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(n.mediatype()))
                .header("Content-Disposition", ContentDisposition.attachment().filename(n.filnavn()).build().toString())
                .body(n.innhold());
    }
}
