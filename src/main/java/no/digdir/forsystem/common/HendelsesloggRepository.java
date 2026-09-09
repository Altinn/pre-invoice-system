package no.digdir.forsystem.common;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface HendelsesloggRepository extends ListCrudRepository<Hendelseslogg, Long> {

    /** Most recent audit rows first, for the audit page. */
    List<Hendelseslogg> findTop200ByOrderByTidspunktDescIdDesc();
}
