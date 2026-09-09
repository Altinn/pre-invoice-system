package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface EksportfilRepository extends ListCrudRepository<Eksportfil, Long> {

    List<Eksportfil> findByKjoringIdOrderByType(Long kjoringId);
}
