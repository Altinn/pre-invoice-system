package no.digdir.forsystem.common;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface HendelsesloggRepository extends ListCrudRepository<Hendelseslogg, Long> {

    /** Most recent audit rows first (tests; the audit page pages through HendelsesloggSok since specs/014). */
    List<Hendelseslogg> findTop200ByOrderByTidspunktDescIdDesc();

    /** Every audit row for one entity (e.g. a run's history, specs/006), newest first. */
    List<Hendelseslogg> findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc(String entitet, Long entitetId);
}
