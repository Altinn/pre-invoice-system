package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataImportRepository extends ListCrudRepository<BruksdataImport, Long> {

    List<BruksdataImport> findAllByOrderByLastetAtDesc();

    /** Prior imports for a period that are not already rejected — replaced on re-import (docs/03 §4). */
    List<BruksdataImport> findByPeriodeAndStatusNot(LocalDate periode, String status);
}
