package no.digdir.forsystem.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Approval rules. {@code sidemannskontroll} (four-eyes): the person who generated a run may not approve
 * it — decided by Økonomi 2026-10-13 (BF1 T13). On by default; only the offline {@code local} profile,
 * which has a single stub user, turns it off.
 */
@ConfigurationProperties(prefix = "forsystem.godkjenning")
public record GodkjenningKonfig(@DefaultValue("true") boolean sidemannskontroll) {
}
