package no.digdir.forsystem.usage;

import java.time.LocalDate;

/**
 * Lets the import guard against clobbering usage a run already depends on, without depending on the
 * billing module (dependency direction is billing → usage, docs/02). Billing supplies the adapter.
 */
public interface KjoringStatusPort {

    /** True if a non-FORKASTET fakturakjøring already exists for the period. */
    boolean finnesIkkeForkastetKjoring(LocalDate periode);
}
