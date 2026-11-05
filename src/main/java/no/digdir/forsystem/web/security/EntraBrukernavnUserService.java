package no.digdir.forsystem.web.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Names Entra users by a username colleagues recognise rather than the opaque {@code sub}
 * (specs/010 FR-011): the first of {@code preferred_username}, {@code email}, {@code upn} that the
 * token carries, else {@code sub}.
 *
 * <p>This replaces the {@code user-name-attribute: preferred_username} provider setting. With that
 * setting Spring Security requires the claim, and a token without it fails the login callback with
 * an {@code IllegalArgumentException} — a 500 on {@code /login/oauth2/code/entra}, and, since Entra
 * signs the user straight back in, a loop the user cannot leave. Here a missing claim only means a
 * less friendly name; {@code sub} is always present in an ID token.
 */
class EntraBrukernavnUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    static final List<String> BRUKERNAVN_CLAIMS = List.of("preferred_username", "email", "upn", "sub");

    private static final Logger LOG = LoggerFactory.getLogger(EntraBrukernavnUserService.class);

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    EntraBrukernavnUserService() {
        this(new OidcUserService());
    }

    EntraBrukernavnUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(request);
        String claim = BRUKERNAVN_CLAIMS.stream()
                .filter(c -> user.getAttribute(c) != null && !user.getAttribute(c).toString().isBlank())
                .findFirst()
                .orElseThrow(); // unreachable: sub is mandatory in an ID token
        if (!claim.equals(BRUKERNAVN_CLAIMS.getFirst())) {
            LOG.warn("ID-tokenet mangler preferred_username; brukeren registreres med claim '{}'", claim);
        }
        return new DefaultOidcUser(user.getAuthorities(), user.getIdToken(), user.getUserInfo(), claim);
    }
}
