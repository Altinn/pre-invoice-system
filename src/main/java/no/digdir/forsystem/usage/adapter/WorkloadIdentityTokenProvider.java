package no.digdir.forsystem.usage.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import no.digdir.forsystem.common.Regelbrudd;
import no.digdir.forsystem.usage.DwhEgenskaper;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Entra token for the datavarehus API via AKS workload identity: exchanges the projected federated
 * token ({@code AZURE_FEDERATED_TOKEN_FILE}) for an access token with the client-credentials grant.
 * Plain OAuth2 over HTTP — no Azure SDK on the compile classpath (docs/02); the environment variables
 * are the ones the workload-identity webhook injects (the same identity the PostgreSQL plugin uses).
 * Tokens are cached until five minutes before expiry.
 */
final class WorkloadIdentityTokenProvider implements DwhTokenProvider {

    private final RestClient http;
    private final String scope;
    private final Map<String, String> env;
    private String token;
    private Instant utløper = Instant.MIN;

    WorkloadIdentityTokenProvider(RestClient.Builder builder, DwhEgenskaper egenskaper, Map<String, String> env) {
        if (egenskaper.scope() == null || egenskaper.scope().isBlank()) {
            throw new IllegalStateException("forsystem.dwh.scope må settes når forsystem.dwh.auth=entra");
        }
        this.http = builder.build();
        this.scope = egenskaper.scope();
        this.env = env;
    }

    @Override
    public synchronized String token() {
        if (token != null && Instant.now().isBefore(utløper)) {
            return token;
        }
        String authority = env.getOrDefault("AZURE_AUTHORITY_HOST", "https://login.microsoftonline.com/");
        String url = (authority.endsWith("/") ? authority : authority + "/") + påkrevd("AZURE_TENANT_ID") + "/oauth2/v2.0/token";
        var skjema = new LinkedMultiValueMap<String, String>();
        skjema.add("grant_type", "client_credentials");
        skjema.add("client_id", påkrevd("AZURE_CLIENT_ID"));
        skjema.add("scope", scope);
        skjema.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        skjema.add("client_assertion", lesFederertToken());
        try {
            TokenSvar svar = http.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(skjema).retrieve().body(TokenSvar.class);
            if (svar == null || svar.access_token() == null) {
                throw new Regelbrudd("Fikk ikke token for datavarehuset");
            }
            token = svar.access_token();
            utløper = Instant.now().plus(Duration.ofSeconds(Math.max(0, svar.expires_in() - 300)));
            return token;
        } catch (RestClientException e) {
            throw new Regelbrudd("Fikk ikke token for datavarehuset: " + e.getMessage());
        }
    }

    private String lesFederertToken() {
        try {
            return Files.readString(Path.of(påkrevd("AZURE_FEDERATED_TOKEN_FILE"))).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese federert token", e);
        }
    }

    private String påkrevd(String navn) {
        String v = env.get(navn);
        if (v == null || v.isBlank()) {
            throw new Regelbrudd("Miljøvariabelen " + navn + " mangler (workload identity er ikke satt opp)");
        }
        return v;
    }

    @SuppressWarnings("java:S116") // field names follow the OAuth2 token response
    record TokenSvar(String access_token, long expires_in) {
    }
}
