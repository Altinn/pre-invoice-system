package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataRepository extends ListCrudRepository<Bruksdata, Long> {

    List<Bruksdata> findByImportId(Long importId);

    List<Bruksdata> findByPeriode(LocalDate periode);

    long countByImportId(Long importId);
}
