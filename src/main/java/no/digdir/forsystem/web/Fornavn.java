package no.digdir.forsystem.web;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The first name the start page greets with (specs/015): the OIDC {@code given_name}, else the first word
 * of {@code name}, else the part of an e-mail-shaped username before the first {@code .} or {@code @},
 * capitalised. {@code null} when none of these exist (the local stub user {@code dev}), so the greeting
 * simply has no name — a guessed name is worse than none.
 */
final class Fornavn {

    private static final Locale NORSK = Locale.forLanguageTag("nb-NO");

    private Fornavn() {
    }

    static String av(Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            String gitt = oidc.getGivenName();
            if (gitt != null && !gitt.isBlank()) {
                return gitt.strip();
            }
            String fraNavn = fraFulltNavn(oidc.getFullName());
            if (fraNavn != null) {
                return fraNavn;
            }
        }
        return fraBrukernavn(auth.getName());
    }

    /**
     * {@code "Adi Dahl"} → {@code "Adi"}. The directory form {@code "Dahl, Adi"} gives the part after the
     * comma, and anything else uncertain gives {@code null}: a guessed name is worse than none.
     */
    static String fraFulltNavn(String navn) {
        if (navn == null || navn.isBlank()) {
            return null;
        }
        String rent = navn.strip();
        int komma = rent.indexOf(',');
        if (komma >= 0) {
            rent = rent.substring(komma + 1).strip();
            if (rent.isEmpty()) {
                return null;
            }
        }
        return rent.split("\\s+")[0];
    }

    /** {@code "adi.dahl@digdir.no"} → {@code "Adi"}; anything not e-mail-shaped → {@code null}. */
    static String fraBrukernavn(String brukernavn) {
        if (brukernavn == null || !brukernavn.contains("@")) {
            return null;
        }
        String lokal = brukernavn.substring(0, brukernavn.indexOf('@'));
        int punktum = lokal.indexOf('.');
        String forste = punktum > 0 ? lokal.substring(0, punktum) : lokal;
        if (forste.isBlank() || !forste.chars().allMatch(Character::isLetter)) {
            return null;
        }
        return forste.substring(0, 1).toUpperCase(NORSK) + forste.substring(1).toLowerCase(NORSK);
    }
}
