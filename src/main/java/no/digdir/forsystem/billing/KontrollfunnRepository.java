package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface KontrollfunnRepository extends ListCrudRepository<Kontrollfunn, Long> {

    List<Kontrollfunn> findByKjoringId(Long kjoringId);

    List<Kontrollfunn> findByFakturaId(Long fakturaId);

    /** Every finding of one code in a run, for accepting a whole group (specs/013). */
    List<Kontrollfunn> findByKjoringIdAndKodeOrderById(Long kjoringId, String kode);

    boolean existsByKjoringIdAndAlvorlighet(Long kjoringId, String alvorlighet);

    /** Unaccepted findings of a severity — for ADVARSEL, the warnings that still hold back approval. */
    long countByKjoringIdAndAlvorlighetAndAkseptertAtIsNull(Long kjoringId, String alvorlighet);
}
