package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets the import guard against clobbering usage a run already depends on, without depending on the
 * billing module (dependency direction is billing → usage, docs/02). Billing supplies the adapter.
 */
public interface KjoringStatusPort {

    /** True if a non-FORKASTET fakturakjøring already exists for the period. */
    boolean finnesIkkeForkastetKjoring(LocalDate periode);

    /**
     * Effective invoiced baseline for a period: the complete ordinary import plus every explicitly
     * selected change from exported corrections, in version order.
     */
    Optional<EffektivtGrunnlag> effektivtGrunnlag(LocalDate periode);

    /**
     * What each organisation was last invoiced with in the period's exported runs (specs/019 FR-004): the
     * invoices of the newest exported version that has an invoice for the orgnr. An orgnr that no exported run
     * invoiced (e.g. below the threshold) is absent.
     */
    Map<String, FakturertKunde> fakturerteKunder(LocalDate periode);

    /** Distinct invoiced values, sorted; {@code ""} stands for «none». */
    record FakturertKunde(
            List<String> navn,
            List<String> kundenummer,
            List<String> fakturareferanse,
            List<String> bestillingsnummer) {
    }

    record EffektivtGrunnlag(
            Long sisteEksporterteKjoringId,
            int sisteVersjon,
            Long rotImportId,
            List<Long> eksporterteEndringIder) {
    }
}
