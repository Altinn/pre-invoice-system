package no.digdir.forsystem.registry;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface KundeReferanseRepository extends ListCrudRepository<KundeReferanse, Long> {

    List<KundeReferanse> findByKundeId(Long kundeId);

    void deleteByKundeId(Long kundeId);
}
