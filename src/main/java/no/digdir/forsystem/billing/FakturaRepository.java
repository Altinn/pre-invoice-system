package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface FakturaRepository extends ListCrudRepository<Faktura, Long> {

    List<Faktura> findByKjoringIdOrderByOrdreNr(Long kjoringId);
}
