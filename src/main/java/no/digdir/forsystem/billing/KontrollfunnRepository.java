package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface KontrollfunnRepository extends ListCrudRepository<Kontrollfunn, Long> {

    List<Kontrollfunn> findByKjoringId(Long kjoringId);

    List<Kontrollfunn> findByFakturaId(Long fakturaId);

    boolean existsByKjoringIdAndAlvorlighet(Long kjoringId, String alvorlighet);
}
