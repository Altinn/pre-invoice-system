package no.digdir.forsystem.usage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Datavarehus integration settings ({@code forsystem.dwh.*}). Off by default so local runs and tests
 * never need the DWH; CSV upload works regardless (FR-010).
 *
 * @param enabled               turn the DWH source on
 * @param baseUrl               Data API Builder base URL (without {@code /api})
 * @param entitet               DAB entity to read
 * @param maksRader             {@code $first} — the whole month must fit in one response (OQ-19)
 * @param auth                  {@code none} or {@code entra} (workload identity token, OQ-19)
 * @param scope                 token scope when {@code auth=entra}, e.g. {@code api://<app-id>/.default}
 * @param tilkoblingTimeout     connect timeout
 * @param lesTimeout            read timeout
 * @param avviksgrenseProsent   warn when a product's monthly total deviates more than this from last month
 * @param planlegging           scheduled staging of the previous month
 */
@ConfigurationProperties(prefix = "forsystem.dwh")
public record DwhEgenskaper(
        @DefaultValue("false") boolean enabled,
        String baseUrl,
        @DefaultValue("mv_altinn_usage_monthly") String entitet,
        @DefaultValue("100000") int maksRader,
        @DefaultValue("none") String auth,
        String scope,
        @DefaultValue("5s") Duration tilkoblingTimeout,
        @DefaultValue("60s") Duration lesTimeout,
        @DefaultValue("50") int avviksgrenseProsent,
        @DefaultValue Planlegging planlegging) {

    /**
     * @param enabled stage the previous month automatically (never commits — a FORVALTER confirms)
     * @param cron    when; default 06:00 on the 7th, after the basis is complete on the 6th (OQ-4)
     */
    public record Planlegging(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0 0 6 7 * *") String cron) {
    }
}
