package no.digdir.forsystem.web.security;

/**
 * Application roles, mapped from OIDC claims/groups in prod (docs/02). Held here as a single
 * source of truth so security config and controllers agree on the authority names.
 *
 * <p>Spring Security prefixes granted authorities with {@code ROLE_}; the string constants below
 * are the unprefixed role names used with {@code hasRole(...)}.
 */
public final class Rolle {

    /** Read-only access to registries, runs and exports. */
    public static final String LESER = "LESER";

    /** Maintains registries, imports usage, generates runs. */
    public static final String FORVALTER = "FORVALTER";

    /** Approves and exports runs. */
    public static final String GODKJENNER = "GODKJENNER";

    private Rolle() {
    }
}
