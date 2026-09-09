package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface BruksdataRepository extends ListCrudRepository<Bruksdata, Long> {

    List<Bruksdata> findByImportId(Long importId);

    List<Bruksdata> findByPeriode(LocalDate periode);

    long countByImportId(Long importId);

    /** Remove all usage for a period; used by the re-import replacement (docs/03 §4). */
    @Modifying
    @Query("delete from bruksdata where periode = :periode")
    void deleteByPeriode(@Param("periode") LocalDate periode);
}
