package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface ProduktKildenavnRepository extends ListCrudRepository<ProduktKildenavn, Long> {

    List<ProduktKildenavn> findByKildeOrderByKildenavn(String kilde);

    Optional<ProduktKildenavn> findByKildeAndKildenavn(String kilde, String kildenavn);
}
