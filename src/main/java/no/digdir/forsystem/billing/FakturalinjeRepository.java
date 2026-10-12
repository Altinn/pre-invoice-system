package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface FakturalinjeRepository extends ListCrudRepository<Fakturalinje, Long> {

    List<Fakturalinje> findByFakturaIdOrderById(Long fakturaId);

    /** All lines of a run in one query (reconciliation view, spreadsheet). */
    @Query("select l.* from fakturalinje l join faktura f on f.id = l.faktura_id where f.kjoring_id = :kjoringId"
            + " order by f.ordre_nr, l.id")
    List<Fakturalinje> findByKjoringId(@Param("kjoringId") Long kjoringId);
}
