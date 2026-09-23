package no.digdir.forsystem.web.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Maps the OIDC {@code groups} claim to the application roles in {@link Rolle}.
 *
 * <p>The Entra app registration is configured to emit the user's security-group object ids as the
 * {@code groups} claim in the ID token. Each id configured in {@link GruppeRolleProperties} grants
 * the corresponding {@code ROLE_*} authority; unknown groups are ignored, and roles are deliberately
 * not hierarchical — GODKJENNER (approve/export) and FORVALTER (maintain) are separate duties
 * (docs/02), and a user holds both only by being in both groups.
 *
 * <p>The original authorities are kept alongside the mapped roles, so a user in no configured
 * group is still an authenticated {@code OIDC_USER} with read access ({@code anyRequest()} in
 * {@link SecurityConfig} requires authentication, not a role).
 */
class EntraGruppeRolleMapper implements GrantedAuthoritiesMapper {

    static final String GROUPS_CLAIM = "groups";

    private final Map<String, String> gruppeTilRolle;

    EntraGruppeRolleMapper(GruppeRolleProperties grupper) {
        Map<String, String> mapping = new HashMap<>();
        putIfConfigured(mapping, grupper.leser(), Rolle.LESER);
        putIfConfigured(mapping, grupper.forvalter(), Rolle.FORVALTER);
        putIfConfigured(mapping, grupper.godkjenner(), Rolle.GODKJENNER);
        this.gruppeTilRolle = Map.copyOf(mapping);
    }

    private static void putIfConfigured(Map<String, String> mapping, String gruppeId, String rolle) {
        if (gruppeId != null && !gruppeId.isBlank()) {
            mapping.put(gruppeId, rolle);
        }
    }

    @Override
    public Set<GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                for (String gruppeId : groupsClaim(oidc)) {
                    String rolle = gruppeTilRolle.get(gruppeId);
                    if (rolle != null) {
                        mapped.add(new SimpleGrantedAuthority("ROLE_" + rolle));
                    }
                }
            }
        }
        return mapped;
    }

    private static List<String> groupsClaim(OidcUserAuthority oidc) {
        List<String> groups = oidc.getIdToken().getClaimAsStringList(GROUPS_CLAIM);
        return groups != null ? groups : List.of();
    }
}
