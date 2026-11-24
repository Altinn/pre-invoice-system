package no.digdir.forsystem.usage;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.BrukerContext;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.registry.Produkt;
import no.digdir.forsystem.registry.ProduktKildenavn;
import no.digdir.forsystem.registry.ProduktKildenavnRepository;
import no.digdir.forsystem.registry.ProduktKildenavnService;
import no.digdir.forsystem.registry.ProduktRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usage from the datavarehus (specs/001-dwh-usage-import): fetch a period through
 * {@link DwhUsageClient}, aggregate daily rows to monthly via the product-name mapping, collect the
 * customer values CRM delivers through the DWH (specs/018), and hand everything to the shared
 * {@link UsageImportService} pipeline. Two entry points:
 *
 * <ul>
 *   <li>{@link #forhaandsvis} — on demand by a FORVALTER; the preview is committed like a CSV upload;</li>
 *   <li>{@link #stage} — scheduled; archives the payload and records a {@code MOTTATT} import that a
 *       FORVALTER later confirms with {@link #bekreftStaget}. Nothing is ever committed automatically.</li>
 * </ul>
 */
@Service
public class DwhImportService {

    private final ObjectProvider<DwhUsageClient> klient;
    private final DwhEgenskaper egenskaper;
    private final UsageImportService usage;
    private final BruksdataImportRepository importer;
    private final ProduktKildenavnRepository kildenavn;
    private final ProduktRepository produkter;
    private final FileArchive arkiv;
    private final AuditService audit;
    private final BrukerContext brukerContext;

    DwhImportService(ObjectProvider<DwhUsageClient> klient, DwhEgenskaper egenskaper, UsageImportService usage,
                     BruksdataImportRepository importer, ProduktKildenavnRepository kildenavn,
                     ProduktRepository produkter, FileArchive arkiv, AuditService audit,
                     BrukerContext brukerContext) {
        this.klient = klient;
        this.egenskaper = egenskaper;
        this.usage = usage;
        this.importer = importer;
        this.kildenavn = kildenavn;
        this.produkter = produkter;
        this.arkiv = arkiv;
        this.audit = audit;
        this.brukerContext = brukerContext;
    }

    /** Whether the DWH source is configured; the UI hides the fetch action otherwise. */
    public boolean aktiv() {
        return egenskaper.enabled() && klient.getIfAvailable() != null;
    }

    /** Fetch a period now and build its preview (not persisted; commit with {@link UsageImportService#importer}). */
    @Transactional(readOnly = true)
    public Forhaandsvisning forhaandsvis(Periode periode) {
        DwhSvar svar = hent(periode);
        return bygg(periode, new Raadata(svar.raadata(), svar.hentetAt(), null, null), null);
    }

    /**
     * Fetch the period and stage it for review: archive the payload and record a {@code MOTTATT} import
     * without usage rows. Does nothing if the period already has a committed import, or if the same
     * payload is already staged (so a retried schedule is harmless).
     */
    @Transactional
    public Optional<BruksdataImport> stage(Periode periode) {
        List<BruksdataImport> eksisterende =
                importer.findByPeriodeAndStatusNot(periode.førsteDag(), Importstatus.AVVIST.name());
        if (eksisterende.stream().anyMatch(i -> Importstatus.VALIDERT.name().equals(i.status()))) {
            return Optional.empty();
        }
        DwhSvar svar = hent(periode);
        Forhaandsvisning fv = bygg(periode, new Raadata(svar.raadata(), svar.hentetAt(), null, null), null);
        Raadata arkivert = usage.arkiver(periode, fv.raadata());

        for (BruksdataImport staget : eksisterende) {
            if (Objects.equals(staget.raadataSha256(), arkivert.sha256())) {
                return Optional.of(staget);
            }
            importer.save(staget.medStatus(Importstatus.AVVIST.name()));
            audit.logg(Handling.AVVISTE, "BRUKSDATA_IMPORT", staget.id(),
                    Map.of("periode", periode.toString(), "aarsak", "erstattet av nyere henting fra datavarehus"));
        }

        BruksdataImport lagret = importer.save(new BruksdataImport(null, filnavn(periode), periode.førsteDag(),
                Kilde.DWH.name(), Importstatus.MOTTATT.name(), fv.antallGyldige(), brukerContext.naavaerendeBruker(),
                OffsetDateTime.now(), arkivert.url(), arkivert.sha256(), arkivert.hentetAt()));
        audit.logg(Handling.HENTET, "BRUKSDATA_IMPORT", lagret.id(), Map.of(
                "periode", periode.toString(), "raadataSha256", arkivert.sha256(),
                "gyldige", fv.antallGyldige(), "avviste", fv.antallAvviste()));
        return Optional.of(lagret);
    }

    /**
     * Build the preview from an uploaded CSV export of the datavarehus view — the fallback while the API
     * is unreachable (OQ-19). Same aggregation and mapping as a fetch; the file itself is
     * archived as the raw payload on confirm. Not persisted here.
     */
    @Transactional(readOnly = true)
    public Forhaandsvisning forhaandsvisFil(Periode periode, String filnavn, byte[] innhold) {
        return fraFil(periode, filnavn, DwhCsv.lesFil(innhold, periode),
                new Raadata(innhold, OffsetDateTime.now(), null, null), null);
    }

    /** The preview of a read export: a flagged one gets its report; an empty flagged one is explained. */
    private Forhaandsvisning fraFil(Periode periode, String filnavn, DwhCsv.Fil fil, Raadata raadata,
                                    Long stagetImportId) {
        if (fil.flagget() && fil.brukte().isEmpty() && fil.avviste().isEmpty()) {
            throw new Regelbrudd("Ingen rader i fila er merket for Altinn-fakturering i " + periode.norskLabel()
                    + ". Fila har " + fil.rapport().totalt() + " rader: " + fil.rapport().utenforPerioden()
                    + " i andre måneder, " + fil.rapport().dpv() + " DPV og " + fil.rapport().utelatt()
                    + " utelatt av datavarehuset.");
        }
        Forhaandsvisning fv = bygg(periode, filnavn, fil.brukte(), fil.avviste(), fil.advarsler(), raadata,
                stagetImportId);
        return fil.flagget() ? fv.medFil(fil.rapport(), fv.kildegrupper()) : fv;
    }

    /** Rebuild the preview of a staged import from its archived payload (mapping/registry may have changed). */
    @Transactional(readOnly = true)
    public Forhaandsvisning forhaandsvisStaget(Long importId) {
        BruksdataImport imp = usage.hentImport(importId);
        if (!Kilde.DWH.name().equals(imp.kilde()) || !Importstatus.MOTTATT.name().equals(imp.status())) {
            throw new Regelbrudd("Import " + importId + " venter ikke på bekreftelse");
        }
        Raadata r = new Raadata(arkiv.hent(imp.raadataUrl()), imp.hentetAt(), imp.raadataUrl(), imp.raadataSha256());
        return bygg(new Periode(imp.periode()), r, imp.id());
    }

    /** Confirm a staged import: re-validate from the archive, then commit through the shared pipeline. */
    @Transactional
    public BruksdataImport bekreftStaget(Long importId) {
        return usage.importer(forhaandsvisStaget(importId));
    }

    private DwhSvar hent(Periode periode) {
        DwhUsageClient k = klient.getIfAvailable();
        if (!egenskaper.enabled() || k == null) {
            throw new Regelbrudd("Henting fra datavarehus er ikke slått på (forsystem.dwh.enabled)");
        }
        return k.hentMaaned(periode);
    }

    private Forhaandsvisning bygg(Periode periode, Raadata raadata, Long stagetImportId) {
        DwhCsv.Fil flagget = DwhRespons.lesFlagget(raadata.innhold(), periode);
        if (flagget != null) {
            return fraFil(periode, filnavn(periode), flagget, raadata, stagetImportId);
        }
        return bygg(periode, filnavn(periode), DwhRespons.les(raadata.innhold()), List.of(), List.of(), raadata,
                stagetImportId);
    }

    /** {@code lesAvviste}: rows the file reader already rejected, merged into the staging report. */
    private Forhaandsvisning bygg(Periode periode, String filnavn, List<DwhRad> rader, List<AvvistRad> lesAvviste,
                                  List<String> lesAdvarsler, Raadata raadata, Long stagetImportId) {
        if (rader.isEmpty() && lesAvviste.isEmpty()) {
            throw new Regelbrudd("Ingen bruksdata i datavarehus for " + periode.norskLabel() + " ennå");
        }
        DwhAggregering.Resultat agg = DwhAggregering.aggreger(periode, rader, kobling());
        List<AvvistRad> avviste = new ArrayList<>(lesAvviste);
        avviste.addAll(agg.avviste());
        return usage.vurder(periode, filnavn, Kilde.DWH, agg.rader(), avviste,
                lesAdvarsler, raadata, stagetImportId)
                .medFil(null, agg.kildegrupper())
                .medKunder(DwhRad.kundedata(rader));
    }

    private Map<String, DwhAggregering.Maal> kobling() {
        Map<Long, String> idTilKode = new HashMap<>();
        for (Produkt p : produkter.findAll()) {
            idTilKode.put(p.id(), p.kode());
        }
        Map<String, DwhAggregering.Maal> kobling = new HashMap<>();
        for (ProduktKildenavn k : kildenavn.findByKildeOrderByKildenavn(ProduktKildenavnService.KILDE_DWH)) {
            kobling.put(k.kildenavn(), new DwhAggregering.Maal(idTilKode.get(k.produktId()), Bruksdatatype.valueOf(k.type())));
        }
        return kobling;
    }

    private String filnavn(Periode periode) {
        return "dwh:" + egenskaper.entitet() + ":" + periode;
    }
}
