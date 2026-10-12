package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataImportRepository extends ListCrudRepository<BruksdataImport, Long> {

    List<BruksdataImport> findAllByOrderByLastetAtDesc();

    /** Prior imports for a period that are not already rejected — replaced on re-import (docs/03 §4). */
    List<BruksdataImport> findByPeriodeAndStatusNot(LocalDate periode, String status);

    /** The latest import for a period with the given status — the period's current one when VALIDERT. */
    Optional<BruksdataImport> findFirstByPeriodeAndStatusOrderByIdDesc(LocalDate periode, String status);
}
