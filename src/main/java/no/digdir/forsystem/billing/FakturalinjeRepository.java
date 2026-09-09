package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface FakturalinjeRepository extends ListCrudRepository<Fakturalinje, Long> {

    List<Fakturalinje> findByFakturaIdOrderById(Long fakturaId);
}
