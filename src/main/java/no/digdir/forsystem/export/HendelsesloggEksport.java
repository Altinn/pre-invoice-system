package no.digdir.forsystem.export;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.Detaljtekst;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Hendelsesfilter;
import no.digdir.forsystem.common.Hendelseslogg;
import no.digdir.forsystem.common.HendelsesloggSok;
import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The event log as CSV or XLSX (specs/014, Økonomi's kickoff row 24): every row matching the page's filters —
 * the complete log when there are none — newest first, times in Norwegian time, details readable. The download
 * is itself logged.
 */
@Service
public class HendelsesloggEksport {

    static final List<String> KOLONNER = List.of("tidspunkt", "bruker", "handling", "entitet", "entitet_id",
            "periode", "detaljer", "detaljer_json");

    private final HendelsesloggSok sok;
    private final RegnearkEksport regneark;
    private final AuditService audit;

    HendelsesloggEksport(HendelsesloggSok sok, RegnearkEksport regneark, AuditService audit) {
        this.sok = sok;
        this.regneark = regneark;
        this.audit = audit;
    }

    /** {@code idag} names the file only; the content does not depend on it. */
    @Transactional   // not read-only: it writes the LASTET_NED audit row
    public EksportService.Nedlasting lastNed(Hendelsesfilter filter, String format, LocalDate idag) {
        List<Hendelseslogg> rader = sok.alle(filter);
        List<List<Object>> tabell = new ArrayList<>(rader.size());
        for (Hendelseslogg h : rader) {
            tabell.add(Arrays.asList(
                    h.tidspunkt().atZoneSameInstant(Hendelsesfilter.OSLO).toLocalDateTime(),
                    h.bruker(), h.handling(), h.entitet(), h.entitetId(),
                    Detaljtekst.periode(h.detaljer()), Detaljtekst.les(h.detaljer()),
                    h.detaljer() == null ? null : h.detaljer().json()));
        }
        String navn = "hendelseslogg-" + idag + (filter.erTom() ? "" : "-filtrert");
        EksportService.Nedlasting n = switch (format) {
            case "csv" -> new EksportService.Nedlasting(navn + ".csv", "text/csv", regneark.csv(KOLONNER, tabell));
            case "xlsx" -> new EksportService.Nedlasting(navn + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    regneark.xlsx("hendelseslogg", KOLONNER, tabell));
            default -> throw new Regelbrudd("Ukjent format: " + format + " (csv eller xlsx)");
        };
        Map<String, Object> detaljer = new LinkedHashMap<>();
        detaljer.put("liste", "hendelseslogg");
        detaljer.put("format", format.toUpperCase());
        detaljer.put("antall", rader.size());
        if (!filter.erTom()) {
            detaljer.put("filter", filter.verdier());
        }
        audit.logg(Handling.LASTET_NED, "HENDELSESLOGG", null, detaljer);
        return n;
    }
}
