package no.digdir.forsystem.common;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The current user's name, for audit stamping and {@code oppdatert_av} columns. Resolved from the
 * security context regardless of how the user authenticated (OIDC, local stub, test mock).
 */
@Component
public class BrukerContext {

    /** The authenticated principal's name, or {@code "system"} when there is none (e.g. startup). */
    public String naavaerendeBruker() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
