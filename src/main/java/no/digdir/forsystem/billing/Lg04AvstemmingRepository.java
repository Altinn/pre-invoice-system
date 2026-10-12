package no.digdir.forsystem.billing;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface Lg04AvstemmingRepository extends ListCrudRepository<Lg04Avstemming, Long> {

    Optional<Lg04Avstemming> findByKjoringId(Long kjoringId);
}
