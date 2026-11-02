package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface FakturakjoringRepository extends ListCrudRepository<Fakturakjoring, Long> {

    boolean existsByPeriodeAndStatusNot(LocalDate periode, String status);

    boolean existsByPeriodeAndStatusIn(LocalDate periode, List<String> statuser);

    /** The single active (non-FORKASTET) run for a period, if any — enforced unique by a partial index. */
    Optional<Fakturakjoring> findFirstByPeriodeAndStatusNot(LocalDate periode, String status);

    Optional<Fakturakjoring> findFirstByPeriodeOrderByVersjonDesc(LocalDate periode);

    Optional<Fakturakjoring> findFirstByPeriodeAndStatusOrderByVersjonDesc(LocalDate periode, String status);

    Optional<Fakturakjoring> findFirstByPeriodeAndTypeAndStatusOrderByVersjonAsc(
            LocalDate periode, String type, String status);

    @Query("select coalesce(max(versjon), 0) + 1 from fakturakjoring where periode = :periode")
    int nesteVersjon(LocalDate periode);

    List<Fakturakjoring> findAllByOrderByGenerertAtDesc();
}
