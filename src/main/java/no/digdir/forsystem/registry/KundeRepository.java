package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface KundeRepository extends ListCrudRepository<Kunde, Long> {

    Optional<Kunde> findByOrganisasjonsnummer(String organisasjonsnummer);

    List<Kunde> findAllByOrderByVirksomhetsnavn();

    boolean existsByOrganisasjonsnummer(String organisasjonsnummer);
}
