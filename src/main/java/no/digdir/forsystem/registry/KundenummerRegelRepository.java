package no.digdir.forsystem.registry;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface KundenummerRegelRepository extends ListCrudRepository<KundenummerRegel, Long> {

    List<KundenummerRegel> findByKundeId(Long kundeId);

    void deleteByKundeId(Long kundeId);
}
