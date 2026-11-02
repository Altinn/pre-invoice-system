package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataSammenligningRepository
        extends ListCrudRepository<BruksdataSammenligning, Long> {

    Optional<BruksdataSammenligning> findByKandidatImportIdAndGrunnlagKjoringId(
            Long kandidatImportId, Long grunnlagKjoringId);

    Optional<BruksdataSammenligning> findFirstByKandidatImportIdOrderByOpprettetAtDesc(Long kandidatImportId);

    List<BruksdataSammenligning> findByPeriodeAndStatus(LocalDate periode, String status);
}
