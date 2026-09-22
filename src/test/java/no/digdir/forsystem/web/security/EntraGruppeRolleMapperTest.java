package no.digdir.forsystem.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class EntraGruppeRolleMapperTest {

    // Deliberately fake ids — the real group object ids live in application-prod.yaml.
    private static final String LESER_GRUPPE = "11111111-1111-1111-1111-111111111111";
    private static final String FORVALTER_GRUPPE = "22222222-2222-2222-2222-222222222222";
    private static final String GODKJENNER_GRUPPE = "33333333-3333-3333-3333-333333333333";

    private final EntraGruppeRolleMapper mapper = new EntraGruppeRolleMapper(
            new GruppeRolleProperties(LESER_GRUPPE, FORVALTER_GRUPPE, GODKJENNER_GRUPPE));

    @Test
    void mapsConfiguredGroupsToRoles() {
        Set<GrantedAuthority> mapped = mapper.mapAuthorities(
                List.of(oidcAuthority(List.of(LESER_GRUPPE, GODKJENNER_GRUPPE))));

        assertThat(mapped)
                .contains(new SimpleGrantedAuthority("ROLE_LESER"),
                        new SimpleGrantedAuthority("ROLE_GODKJENNER"))
                .doesNotContain(new SimpleGrantedAuthority("ROLE_FORVALTER"));
    }

    @Test
    void ignoresUnknownGroupsAndKeepsOriginalAuthorities() {
        OidcUserAuthority original = oidcAuthority(List.of("99999999-9999-9999-9999-999999999999"));

        Set<GrantedAuthority> mapped = mapper.mapAuthorities(List.of(original));

        assertThat(mapped).containsExactly(original);
    }

    @Test
    void missingGroupsClaimGrantsNoRoles() {
        OidcUserAuthority original = oidcAuthority(null);

        Set<GrantedAuthority> mapped = mapper.mapAuthorities(List.of(original));

        assertThat(mapped).containsExactly(original);
    }

    @Test
    void unsetGroupIdMapsNobody() {
        EntraGruppeRolleMapper unconfigured = new EntraGruppeRolleMapper(
                new GruppeRolleProperties(null, "", "  "));

        Set<GrantedAuthority> mapped = unconfigured.mapAuthorities(
                List.of(oidcAuthority(List.of(LESER_GRUPPE, FORVALTER_GRUPPE, GODKJENNER_GRUPPE))));

        assertThat(mapped).noneMatch(a -> a.getAuthority().startsWith("ROLE_"));
    }

    private static OidcUserAuthority oidcAuthority(List<String> groups) {
        Map<String, Object> claims = groups == null
                ? Map.of("sub", "user")
                : Map.of("sub", "user", EntraGruppeRolleMapper.GROUPS_CLAIM, groups);
        OidcIdToken idToken = new OidcIdToken(
                "token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new OidcUserAuthority(idToken, null);
    }
}
