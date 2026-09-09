package no.digdir.forsystem.registry;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Offline BRREG stub for every non-prod profile (local/test/demo). Reports UTILGJENGELIG so the app
 * runs fully offline with the deliberately-fake demo/test data and no network (docs/02): creation is
 * never blocked and no false name-warnings are raised. The real validation lives in
 * {@link BrregHttpOppslag}, active in prod.
 */
@Component
@Profile("!prod")
class PermissivBrregOppslag implements BrregOppslag {

    @Override
    public BrregSvar slaaOpp(String organisasjonsnummer) {
        return BrregSvar.UTILGJENGELIG;
    }
}
