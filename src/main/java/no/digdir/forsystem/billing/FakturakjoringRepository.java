package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface FakturakjoringRepository extends ListCrudRepository<Fakturakjoring, Long> {

    // The per-month queries below never see an AVREGNING run: it is anchored on 1 January of its year but is not
    // part of January's runs or versions (specs/020).

    @Query("select exists (select 1 from fakturakjoring where periode = :periode and status <> :status "
            + "and type <> 'AVREGNING')")
    boolean existsByPeriodeAndStatusNot(LocalDate periode, String status);

    @Query("select exists (select 1 from fakturakjoring where periode = :periode and status in (:statuser) "
            + "and type <> 'AVREGNING')")
    boolean existsByPeriodeAndStatusIn(LocalDate periode, List<String> statuser);

    /** The single active (non-FORKASTET) run for a period, if any — enforced unique by a partial index. */
    @Query("select * from fakturakjoring where periode = :periode and status <> :status and type <> 'AVREGNING' "
            + "order by versjon limit 1")
    Optional<Fakturakjoring> findFirstByPeriodeAndStatusNot(LocalDate periode, String status);

    @Query("select * from fakturakjoring where periode = :periode and type <> 'AVREGNING' "
            + "order by versjon desc limit 1")
    Optional<Fakturakjoring> findFirstByPeriodeOrderByVersjonDesc(LocalDate periode);

    @Query("select * from fakturakjoring where periode = :periode and status = :status and type <> 'AVREGNING' "
            + "order by versjon desc limit 1")
    Optional<Fakturakjoring> findFirstByPeriodeAndStatusOrderByVersjonDesc(LocalDate periode, String status);

    @Query("select * from fakturakjoring where periode = :periode and status = :status and type <> 'AVREGNING' "
            + "order by versjon desc")
    List<Fakturakjoring> findByPeriodeAndStatusOrderByVersjonDesc(LocalDate periode, String status);

    @Query("select * from fakturakjoring where periode = :periode and status in (:statuser) "
            + "and type <> 'AVREGNING'")
    List<Fakturakjoring> findByPeriodeAndStatusIn(LocalDate periode, List<String> statuser);

    Optional<Fakturakjoring> findFirstByPeriodeAndTypeAndStatusOrderByVersjonAsc(
            LocalDate periode, String type, String status);

    @Query("select coalesce(max(versjon), 0) + 1 from fakturakjoring where periode = :periode "
            + "and type <> 'AVREGNING'")
    int nesteVersjon(LocalDate periode);

    /** The next version of the settlement of a year (anchored on its 1 January). */
    @Query("select coalesce(max(versjon), 0) + 1 from fakturakjoring where periode = :periode "
            + "and type = 'AVREGNING'")
    int nesteAvregningsversjon(LocalDate periode);

    @Query("select exists (select 1 from fakturakjoring where periode = :periode and type = 'AVREGNING' "
            + "and status in ('GENERERT', 'GODKJENT'))")
    boolean finnesPaagaaendeAvregning(LocalDate periode);

    List<Fakturakjoring> findAllByOrderByGenerertAtDesc();

    /**
     * The run, row-locked until the transaction ends. Approval and text edits both save the whole
     * record, so they read through this to serialise: whichever comes second sees the first's result
     * (specs/009 R4).
     */
    @Query("select * from fakturakjoring where id = :id for update")
    Optional<Fakturakjoring> finnForOppdatering(Long id);
}
