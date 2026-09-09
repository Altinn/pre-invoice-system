package no.digdir.forsystem.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Provides the {@link StubAuthenticationFilter} bean only in the {@code local} profile. When the
 * bean is present, {@link SecurityConfig} inserts it into the chain; otherwise the app relies on
 * OIDC (prod) or {@code @WithMockUser} (tests).
 */
@Configuration
@Profile("local")
class LocalStubAuthConfig {

    @Bean
    StubAuthenticationFilter stubAuthenticationFilter() {
        return new StubAuthenticationFilter();
    }
}
