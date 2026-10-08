package no.digdir.forsystem.usage.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Workload-identity client-credentials exchange, without the Azure SDK. */
class WorkloadIdentityTokenProviderTest {

    @TempDir Path tmp;

    @Test
    void exchangesFederatedTokenAndCachesResult() throws IOException {
        Path fil = Files.writeString(tmp.resolve("token"), "federert-jwt\n");
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://login.test/tenant-1/oauth2/v2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "klient-1",
                        "scope", "api://dwh/.default",
                        "client_assertion", "federert-jwt")))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        var provider = new WorkloadIdentityTokenProvider(builder, DabUsageClientTest.egenskaper(), Map.of(
                "AZURE_AUTHORITY_HOST", "https://login.test/",
                "AZURE_TENANT_ID", "tenant-1",
                "AZURE_CLIENT_ID", "klient-1",
                "AZURE_FEDERATED_TOKEN_FILE", fil.toString()));

        assertThat(provider.token()).isEqualTo("tok");
        assertThat(provider.token()).isEqualTo("tok");
        server.verify();
    }

    @Test
    void missingWorkloadIdentityEnvironmentFailsClearly() {
        var provider = new WorkloadIdentityTokenProvider(RestClient.builder(), DabUsageClientTest.egenskaper(), Map.of());
        assertThatThrownBy(provider::token).isInstanceOf(Regelbrudd.class).hasMessageContaining("AZURE_TENANT_ID");
    }
}
