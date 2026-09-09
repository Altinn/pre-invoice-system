package no.digdir.forsystem.web.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs in a fixed dev user holding every role, so the app is fully usable offline without an
 * identity provider (docs/04 Phase 0). Wired into the security chain only in the {@code local}
 * profile ({@link LocalStubAuthConfig}); never in test (where {@code @WithMockUser} drives auth)
 * or prod (OIDC).
 */
public final class StubAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEV_USER = "dev";

    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_" + Rolle.LESER),
            new SimpleGrantedAuthority("ROLE_" + Rolle.FORVALTER),
            new SimpleGrantedAuthority("ROLE_" + Rolle.GODKJENNER));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null || !context.getAuthentication().isAuthenticated()) {
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(DEV_USER, "N/A", AUTHORITIES));
        }
        filterChain.doFilter(request, response);
    }
}
