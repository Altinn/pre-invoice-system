package no.digdir.forsystem.web.security;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Authorization rules, shared by every profile. Authentication differs per profile — OIDC in prod
 * ({@code oauth2Login}), a stubbed dev user in {@code local} ({@link LocalStubAuthConfig}), and
 * {@code @WithMockUser} in tests — but the rules below are identical everywhere so tests exercise
 * the real policy (docs/02, docs/04 Phase 1):
 *
 * <ul>
 *   <li>writes (POST/PUT/PATCH/DELETE) require {@code FORVALTER};</li>
 *   <li>everything else requires an authenticated user (any role includes at least {@code LESER});</li>
 *   <li>static assets and Actuator liveness/readiness are open.</li>
 * </ul>
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    Optional<ClientRegistrationRepository> clientRegistrations,
                                    Optional<StubAuthenticationFilter> stubAuth)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/webjars/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Approval and export are the GODKJENNER's job; other writes are FORVALTER's.
                        .requestMatchers(HttpMethod.POST, "/kjoringer/*/godkjenn").hasRole(Rolle.GODKJENNER)
                        .requestMatchers(HttpMethod.POST, "/kjoringer/*/eksporter").hasRole(Rolle.GODKJENNER)
                        .requestMatchers(HttpMethod.POST, "/**").hasRole(Rolle.FORVALTER)
                        .requestMatchers(HttpMethod.PUT, "/**").hasRole(Rolle.FORVALTER)
                        .requestMatchers(HttpMethod.PATCH, "/**").hasRole(Rolle.FORVALTER)
                        .requestMatchers(HttpMethod.DELETE, "/**").hasRole(Rolle.FORVALTER)
                        .anyRequest().authenticated())
                // Thymeleaf forms carry the CSRF token; the REST surface (future) is exempt.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));

        // Only wire OIDC login when an issuer is configured (prod). Local/test authenticate otherwise.
        if (clientRegistrations.isPresent()) {
            http.oauth2Login(login -> {
                // Group/role claim -> ROLE_* mapping is added when the Entra registration exists.
            });
        }
        // Local profile: auto-login the dev user right after the context is loaded.
        stubAuth.ifPresent(filter -> http.addFilterAfter(filter, SecurityContextHolderFilter.class));
        return http.build();
    }
}
