package no.digdir.forsystem.registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Validates an organisasjonsnummer against Brønnøysundregistrene via the open API at data.brreg.no
 * (K-16). Checks the main unit register, then the sub-unit register. Any transport problem yields
 * {@code UTILGJENGELIG} so the caller skips the check rather than block on BRREG being down. Active
 * only in prod; offline profiles use {@link PermissivBrregOppslag}.
 */
@Component
@Profile("prod")
class BrregHttpOppslag implements BrregOppslag {

    private final RestClient klient;

    BrregHttpOppslag(@Value("${forsystem.brreg.base-url:https://data.brreg.no/enhetsregisteret/api}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(4000);
        this.klient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public BrregSvar slaaOpp(String organisasjonsnummer) {
        BrregSvar enhet = slåOpp("/enheter/{o}", organisasjonsnummer);
        if (enhet.status() == BrregSvar.Status.IKKE_FUNNET) {
            return slåOpp("/underenheter/{o}", organisasjonsnummer);
        }
        return enhet;
    }

    private BrregSvar slåOpp(String sti, String orgnr) {
        try {
            return klient.get().uri(sti, orgnr).exchange((request, response) -> {
                int kode = response.getStatusCode().value();
                if (response.getStatusCode().is2xxSuccessful()) {
                    BrregEnhet enhet = response.bodyTo(BrregEnhet.class);
                    return BrregSvar.funnet(enhet == null ? null : enhet.navn());
                }
                if (kode == 404) {
                    return BrregSvar.IKKE_FUNNET;
                }
                return BrregSvar.UTILGJENGELIG;
            });
        } catch (RestClientException e) {
            return BrregSvar.UTILGJENGELIG;
        }
    }

    /** Minimal projection of the BRREG entity response (unknown fields ignored by Jackson). */
    record BrregEnhet(String organisasjonsnummer, String navn) {
    }
}
