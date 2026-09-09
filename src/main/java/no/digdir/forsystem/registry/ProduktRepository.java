package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface ProduktRepository extends ListCrudRepository<Produkt, Long> {

    Optional<Produkt> findByKode(String kode);

    List<Produkt> findAllByOrderByKode();

    List<Produkt> findByAktivTrue();
}
