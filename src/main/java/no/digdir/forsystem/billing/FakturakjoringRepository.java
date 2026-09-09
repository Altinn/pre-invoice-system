package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface FakturakjoringRepository extends ListCrudRepository<Fakturakjoring, Long> {

    boolean existsByPeriodeAndStatusNot(LocalDate periode, String status);

    /** The single active (non-FORKASTET) run for a period, if any — enforced unique by a partial index. */
    Optional<Fakturakjoring> findFirstByPeriodeAndStatusNot(LocalDate periode, String status);

    List<Fakturakjoring> findAllByOrderByGenerertAtDesc();
}
