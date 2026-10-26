package no.digdir.forsystem.billing;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import no.digdir.forsystem.archive.FileArchive;
import no.digdir.forsystem.common.AuditService;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Test environments only: "Nullstill periode" permanently deletes every run (including exported ones)
 * and every usage import of a period, with their invoices, findings, reconciliation, export files and
 * archived raw data, so testers can run the same month end to end again with the same file.
 *
 * <p>This deliberately breaks the production rules — exported runs are final and history is kept
 * (constitution III). The bean only exists when {@code forsystem.testverktoy.enabled=true}, which is
 * set for local and tt02, never prod. The deletion itself is logged in hendelseslogg.
 */
@Service
@ConditionalOnProperty(prefix = "forsystem.testverktoy", name = "enabled", havingValue = "true")
public class NullstillingService {

    private static final Logger LOG = LoggerFactory.getLogger(NullstillingService.class);

    private static final String KJORINGER = "select id from fakturakjoring where periode = ?";
    private static final String IMPORTER = "select id from bruksdata_import where periode = ?";

    /** A period that has something to reset. */
    public record Periodedata(Periode periode, int antallKjoringer, int antallImporter, boolean eksportert) {
    }

    /** What a reset removed. */
    public record Nullstilt(Periode periode, int kjoringer, int fakturaer, int importer, int bruksdatarader,
                            int filer) {
    }

    private final JdbcTemplate jdbc;
    private final FileArchive arkiv;
    private final AuditService audit;

    NullstillingService(JdbcTemplate jdbc, FileArchive arkiv, AuditService audit) {
        this.jdbc = jdbc;
        this.arkiv = arkiv;
        this.audit = audit;
    }

    /** Every period with at least one run or usage import, newest first. */
    @Transactional(readOnly = true)
    public List<Periodedata> perioderMedData() {
        return jdbc.query("""
                select p.periode,
                       (select count(*) from fakturakjoring k where k.periode = p.periode) as kjoringer,
                       (select count(*) from bruksdata_import i where i.periode = p.periode) as importer,
                       exists (select 1 from fakturakjoring k
                                where k.periode = p.periode and k.status = 'EKSPORTERT') as eksportert
                  from (select periode from fakturakjoring union select periode from bruksdata_import) p
                 order by p.periode desc
                """, (rs, i) -> new Periodedata(new Periode(rs.getDate("periode").toLocalDate()),
                rs.getInt("kjoringer"), rs.getInt("importer"), rs.getBoolean("eksportert")));
    }

    @Transactional
    public Nullstilt nullstill(Periode periode) {
        Date p = Date.valueOf(periode.førsteDag());
        List<Long> kjoringIder = jdbc.queryForList(KJORINGER, Long.class, p);
        List<Long> importIder = jdbc.queryForList(IMPORTER, Long.class, p);
        if (kjoringIder.isEmpty() && importIder.isEmpty()) {
            throw new Regelbrudd("Ingen kjøringer eller bruksdata for " + periode.norskLabel() + " å nullstille");
        }
        List<String> filer = jdbc.queryForList("""
                select e.blob_url from eksportfil e where e.kjoring_id in (%s)
                union all
                select raadata_url from bruksdata_import where periode = ? and raadata_url is not null
                """.formatted(KJORINGER), String.class, p, p);

        // Children before parents, along the foreign keys of V4/V8/V9/V10.
        jdbc.update("delete from lg04_avstemming where kjoring_id in (" + KJORINGER + ")", p);
        jdbc.update("""
                delete from kontrollfunn_bruksdata
                 where kontrollfunn_id in (select id from kontrollfunn where kjoring_id in (%s))
                    or bruksdata_id in (select id from bruksdata where import_id in (%s))
                """.formatted(KJORINGER, IMPORTER), p, p);
        jdbc.update("delete from kontrollfunn where kjoring_id in (" + KJORINGER + ")", p);
        jdbc.update("delete from fakturalinje where faktura_id in (select id from faktura where kjoring_id in ("
                + KJORINGER + "))", p);
        int fakturaer = jdbc.update("delete from faktura where kjoring_id in (" + KJORINGER + ")", p);
        jdbc.update("delete from eksportfil where kjoring_id in (" + KJORINGER + ")", p);
        jdbc.update("delete from fakturakjoring where periode = ?", p);
        int rader = jdbc.update("delete from bruksdata where import_id in (" + IMPORTER + ")", p);
        jdbc.update("delete from bruksdata_import where periode = ?", p);

        Map<String, Object> detaljer = new LinkedHashMap<>();
        detaljer.put("periode", periode.toString());
        detaljer.put("kjoringer", kjoringIder);
        detaljer.put("importer", importIder);
        detaljer.put("fakturaer", fakturaer);
        detaljer.put("bruksdatarader", rader);
        detaljer.put("filer", filer.size());
        audit.logg(Handling.NULLSTILTE, "PERIODE", null, detaljer);

        slettEtterCommit(filer);
        return new Nullstilt(periode, kjoringIder.size(), fakturaer, importIder.size(), rader, filer.size());
    }

    /**
     * Archived files go only once the rows are gone for good: a rollback must not leave rows pointing at
     * deleted files. A file that cannot be removed is merely orphaned, so that is logged, not thrown.
     */
    private void slettEtterCommit(List<String> filer) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String fil : filer) {
                    try {
                        arkiv.slett(fil);
                    } catch (RuntimeException e) {
                        LOG.warn("Kunne ikke slette arkivert fil {} etter nullstilling", fil, e);
                    }
                }
            }
        });
    }
}
