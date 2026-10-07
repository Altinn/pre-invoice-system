package no.digdir.forsystem.usage;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;

import no.digdir.forsystem.common.Periode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Stages the previous month from the datavarehus on a schedule (default 06:00 on the 7th, Oslo time —
 * the basis is complete on the 6th, OQ-4). The scheduler is the only place a period is derived from
 * the clock, and it passes it on explicitly. It only stages; a FORVALTER confirms (constitution VII).
 * Failures are logged and retried on the next trigger; existing data is never touched.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "forsystem.dwh", name = {"enabled", "planlegging.enabled"}, havingValue = "true")
class DwhImportScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DwhImportScheduler.class);
    static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final DwhImportService service;
    private final Clock klokke;

    @Autowired
    DwhImportScheduler(DwhImportService service) {
        this(service, Clock.system(OSLO));
    }

    DwhImportScheduler(DwhImportService service, Clock klokke) {
        this.service = service;
        this.klokke = klokke;
    }

    @Scheduled(cron = "${forsystem.dwh.planlegging.cron:0 0 6 7 * *}", zone = "Europe/Oslo")
    void stageForrigeMaaned() {
        Periode periode = Periode.fra(YearMonth.now(klokke).minusMonths(1));
        try {
            service.stage(periode).ifPresentOrElse(
                    imp -> LOG.info("Bruksdata for {} hentet fra datavarehus og venter på bekreftelse (import {})",
                            periode, imp.id()),
                    () -> LOG.info("Bruksdata for {} er allerede importert; ingen henting", periode));
        } catch (RuntimeException e) {
            LOG.warn("Henting av bruksdata for {} fra datavarehus feilet: {}", periode, e.getMessage());
        }
    }
}
