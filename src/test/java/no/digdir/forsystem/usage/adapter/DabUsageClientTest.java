package no.digdir.forsystem.usage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;

import no.digdir.forsystem.common.Periode;
import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.DwhEgenskaper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HTTP side of the DAB contract: the exact request, auth header, and transport failures. */
class DabUsageClientTest {

    private static final Periode JAN = Periode.av(2027, 1);
    private static final URI FORVENTET = URI.create("https://dwh.test/api/mv_altinn_usage_monthly"
            + "?$filter=transaction_date%20ge%202027-01-01T00%3A00%3A00Z%20and%20transaction_date%20lt%202027-02-01T00%3A00%3A00Z"
            + "&$first=100000");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void requestsWholeMonthInOnePageAndReturnsRawBytes() {
        server.expect(requestTo(FORVENTET)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"value\":[]}", MediaType.APPLICATION_JSON));

        var svar = klient(null).hentMaaned(JAN);

        assertThat(new String(svar.raadata())).isEqualTo("{\"value\":[]}");
        assertThat(svar.hentetAt()).isNotNull();
        server.verify();
    }

    @Test
    void sendsBearerTokenWhenAuthConfigured() {
        server.expect(requestTo(FORVENTET)).andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"value\":[]}", MediaType.APPLICATION_JSON));
        klient(() -> "tok").hentMaaned(JAN);
        server.verify();
    }

    @Test
    void non2xxFailsTheFetch() {
        server.expect(requestTo(FORVENTET)).andRespond(withServerError());
        assertThatThrownBy(() -> klient(null).hentMaaned(JAN))
                .isInstanceOf(Regelbrudd.class).hasMessageContaining("HTTP 500");
    }

    private DabUsageClient klient(DwhTokenProvider tokens) {
        return new DabUsageClient(builder.build(), egenskaper(), tokens);
    }

    static DwhEgenskaper egenskaper() {
        return new DwhEgenskaper(true, "https://dwh.test", "mv_altinn_usage_monthly", 100000, "none",
                "api://dwh/.default", Duration.ofSeconds(5), Duration.ofSeconds(60), 50,
                new DwhEgenskaper.Planlegging(false, "0 0 6 7 * *"));
    }
}
