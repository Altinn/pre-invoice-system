package no.digdir.forsystem.registry;

/**
 * Port for validating a customer against Brønnøysundregistrene (BRREG) — requirement K-16 / docs/01
 * rule 5. The HTTP adapter queries data.brreg.no in prod; offline profiles use a permissive stub so
 * {@code docker compose up} and tests keep working without network. BRREG is a validation aid, not a
 * hard dependency: an unknown orgnr blocks creation, a name mismatch is only a warning, and when
 * BRREG is unreachable the checks are skipped.
 */
public interface BrregOppslag {

    BrregSvar slaaOpp(String organisasjonsnummer);

    /** Outcome of a BRREG lookup. {@code offisieltNavn} is set only when {@code status == FUNNET}. */
    record BrregSvar(Status status, String offisieltNavn) {

        public enum Status {
            /** The organisasjonsnummer exists in BRREG. */
            FUNNET,
            /** BRREG was reachable and the organisasjonsnummer does not exist. */
            IKKE_FUNNET,
            /** BRREG could not be reached (offline/timeout/error) — validation is skipped. */
            UTILGJENGELIG
        }

        public static final BrregSvar IKKE_FUNNET = new BrregSvar(Status.IKKE_FUNNET, null);
        public static final BrregSvar UTILGJENGELIG = new BrregSvar(Status.UTILGJENGELIG, null);

        public static BrregSvar funnet(String navn) {
            return new BrregSvar(Status.FUNNET, navn);
        }

        public boolean erFunnet() {
            return status == Status.FUNNET;
        }

        public boolean erIkkeFunnet() {
            return status == Status.IKKE_FUNNET;
        }
    }
}
