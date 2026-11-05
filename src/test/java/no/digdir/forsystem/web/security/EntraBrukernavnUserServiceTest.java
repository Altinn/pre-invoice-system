package no.digdir.forsystem.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The Entra login callback names the user by a recognisable claim and never fails for a missing one
 * (specs/010 FR-011). Runs Spring Security's real OIDC login step — token response, ID token, user
 * service, role mapping — with the token endpoint and signature check stubbed, so no network is used.
 */
class EntraBrukernavnUserServiceTest {

    // Deliberately fake ids — the real group object ids live in application-prod.yaml.
    private static final String FORVALTER_GRUPPE = "22222222-2222-2222-2222-222222222222";
    private static final String SUB = "AAAAAAAAAAAAAAAAAAAAAAzxQ0dFqxNY-fake-sub";

    @Test
    void namesTheUserByPreferredUsername() {
        Authentication login = logIn(new EntraBrukernavnUserService(), registration(null),
                claims("preferred_username", "ola.nordmann@digdir.no", "email", "ola@example.org"));

        assertThat(login.getName()).isEqualTo("ola.nordmann@digdir.no");
    }

    @Test
    void fallsBackToEmailWhenPreferredUsernameIsMissing() {
        Authentication login = logIn(new EntraBrukernavnUserService(), registration(null),
                claims("email", "ola.nordmann@digdir.no"));

        assertThat(login.getName()).isEqualTo("ola.nordmann@digdir.no");
    }

    @Test
    void fallsBackToUpnThenSub() {
        assertThat(logIn(new EntraBrukernavnUserService(), registration(null),
                claims("upn", "ola@digdir.no")).getName()).isEqualTo("ola@digdir.no");
        assertThat(logIn(new EntraBrukernavnUserService(), registration(null),
                claims("preferred_username", " ")).getName()).isEqualTo(SUB);
    }

    @Test
    void keepsTheGroupRoles() {
        Map<String, Object> claims = claims("preferred_username", "ola.nordmann@digdir.no");
        claims.put(EntraGruppeRolleMapper.GROUPS_CLAIM, List.of(FORVALTER_GRUPPE));

        Authentication login = logIn(new EntraBrukernavnUserService(), registration(null), claims);

        assertThat(login.getAuthorities()).extracting(a -> a.getAuthority()).contains("ROLE_FORVALTER");
        assertThat(login.getPrincipal()).isInstanceOf(OidcUser.class);
    }

    /** Why the provider setting is gone: with it, a token without the claim fails the callback (a 500). */
    @Test
    void theOldProviderSettingFailsTheLoginWhenTheClaimIsMissing() {
        assertThatThrownBy(() -> logIn(new OidcUserService(), registration("preferred_username"),
                claims("email", "ola.nordmann@digdir.no")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferred_username");
    }

    @Test
    void prodDoesNotRequireAUsernameClaim() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yaml"));
        Properties prod = yaml.getObject();

        assertThat(prod).doesNotContainKey("spring.security.oauth2.client.provider.entra.user-name-attribute");
    }

    private static Authentication logIn(OAuth2UserService<OidcUserRequest, OidcUser> userService,
                                        ClientRegistration registration, Map<String, Object> idTokenClaims) {
        Jwt idToken = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(idTokenClaims))
                .build();
        var provider = new OidcAuthorizationCodeAuthenticationProvider(
                grant -> OAuth2AccessTokenResponse.withToken("access-token")
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(3600)
                        .scopes(registration.getScopes())
                        .additionalParameters(Map.of(OidcParameterNames.ID_TOKEN, "id-token"))
                        .build(),
                userService);
        provider.setJwtDecoderFactory(reg -> token -> idToken);
        provider.setAuthoritiesMapper(new EntraGruppeRolleMapper(
                new GruppeRolleProperties(null, FORVALTER_GRUPPE, null)));

        var request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri(registration.getRedirectUri())
                .scopes(registration.getScopes())
                .state("state")
                .build();
        var response = OAuth2AuthorizationResponse.success("code")
                .redirectUri(registration.getRedirectUri())
                .state("state")
                .build();
        return provider.authenticate(new OAuth2LoginAuthenticationToken(
                registration, new OAuth2AuthorizationExchange(request, response)));
    }

    /** No user-info URI, as the test cannot reach Microsoft Graph; the ID token carries the claims. */
    private static ClientRegistration registration(String userNameAttribute) {
        return ClientRegistration.withRegistrationId("entra")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://forsystem.test/login/oauth2/code/entra")
                .scope("openid", "profile", "email")
                .authorizationUri("https://login.example.test/authorize")
                .tokenUri("https://login.example.test/token")
                .jwkSetUri("https://login.example.test/keys")
                .userNameAttributeName(userNameAttribute)
                .build();
    }

    private static Map<String, Object> claims(String... nameValuePairs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://login.example.test/v2.0");
        claims.put("sub", SUB);
        claims.put("aud", List.of("client-id"));
        claims.put("iat", Instant.now());
        claims.put("exp", Instant.now().plusSeconds(3600));
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            claims.put(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return claims;
    }
}
