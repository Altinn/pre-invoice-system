package no.digdir.forsystem.usage;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataEndringRepository extends ListCrudRepository<BruksdataEndring, Long> {
    List<BruksdataEndring> findBySammenligningIdOrderById(Long sammenligningId);
    long countBySammenligningId(Long sammenligningId);
}
