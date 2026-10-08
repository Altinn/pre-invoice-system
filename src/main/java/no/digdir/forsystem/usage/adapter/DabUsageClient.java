package no.digdir.forsystem.usage.adapter;

import java.net.URI;
import java.time.OffsetDateTime;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.DwhEgenskaper;
import no.digdir.forsystem.usage.DwhSvar;
import no.digdir.forsystem.usage.DwhUsageClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link DwhUsageClient} against the datavarehus's Data API Builder REST endpoint
 * (contract: specs/001-dwh-usage-import/contracts/dwh-api.md). One request per period, filtered on
 * {@code transaction_date} and sized by {@code $first} so the whole month arrives in one page — the
 * view's paging is lossy (OQ-19), so a {@code nextLink} is never followed; {@code DwhRespons} rejects it.
 */
final class DabUsageClient implements DwhUsageClient {

    private final RestClient http;
    private final DwhEgenskaper egenskaper;
    private final DwhTokenProvider tokens;

    DabUsageClient(RestClient http, DwhEgenskaper egenskaper, DwhTokenProvider tokens) {
        this.http = http;
        this.egenskaper = egenskaper;
        this.tokens = tokens;
    }

    @Override
    public DwhSvar hentMaaned(Periode periode) {
        URI uri = uri(periode);
        try {
            byte[] innhold = http.get().uri(uri)
                    .headers(h -> {
                        if (tokens != null) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
                        }
                    })
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new Regelbrudd("Datavarehuset svarte med HTTP " + res.getStatusCode().value()
                                    + " for " + periode);
                        }
                        return res.getBody().readAllBytes();
                    });
            return new DwhSvar(innhold, OffsetDateTime.now());
        } catch (RestClientException e) {
            throw new Regelbrudd("Fikk ikke kontakt med datavarehuset: " + e.getMessage());
        }
    }

    URI uri(Periode periode) {
        String fra = periode.førsteDag() + "T00:00:00Z";
        String til = periode.førsteDag().plusMonths(1) + "T00:00:00Z";
        return UriComponentsBuilder.fromUriString(egenskaper.baseUrl())
                .path("/api/{entitet}")
                .queryParam("$filter", "{filter}")
                .queryParam("$first", egenskaper.maksRader())
                .encode()
                .buildAndExpand(egenskaper.entitet(), "transaction_date ge " + fra + " and transaction_date lt " + til)
                .toUri();
    }
}
