package no.digdir.forsystem.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC group ids that map to the application roles in {@link Rolle}. In Digdir's Entra tenant
 * these are the object ids of the three {@code forsystem-*} security groups; group ids are
 * identifiers, not secrets (docs/02), and the values live in {@code application-prod.yaml} with
 * per-environment env-var overrides.
 *
 * <p>An unset (null/blank) id simply maps nobody to that role, so profiles without OIDC need no
 * configuration here.
 */
@ConfigurationProperties(prefix = "forsystem.security.grupper")
public record GruppeRolleProperties(String leser, String forvalter, String godkjenner) {
}
