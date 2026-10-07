package no.digdir.forsystem.usage.adapter;

import no.digdir.forsystem.usage.DwhEgenskaper;
import no.digdir.forsystem.usage.DwhUsageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the datavarehus adapter only when {@code forsystem.dwh.enabled=true}; otherwise no
 * {@link DwhUsageClient} exists and only CSV upload is offered (FR-010).
 */
@Configuration
@ConditionalOnProperty(prefix = "forsystem.dwh", name = "enabled", havingValue = "true")
class DwhKonfigurasjon {

    @Bean
    DwhUsageClient dwhUsageClient(DwhEgenskaper egenskaper, RestClient.Builder builder) {
        if (egenskaper.baseUrl() == null || egenskaper.baseUrl().isBlank()) {
            throw new IllegalStateException("forsystem.dwh.base-url må settes når forsystem.dwh.enabled=true");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(egenskaper.tilkoblingTimeout());
        factory.setReadTimeout(egenskaper.lesTimeout());
        RestClient http = builder.clone().requestFactory(factory).build();
        DwhTokenProvider tokens = switch (egenskaper.auth()) {
            case "none" -> null;
            case "entra" -> new WorkloadIdentityTokenProvider(builder.clone(), egenskaper, System.getenv());
            default -> throw new IllegalStateException("Ukjent forsystem.dwh.auth: " + egenskaper.auth());
        };
        return new DabUsageClient(http, egenskaper, tokens);
    }
}
