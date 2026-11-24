package no.digdir.forsystem.usage;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface KundeavvikRepository extends ListCrudRepository<Kundeavvik, Long> {
    List<Kundeavvik> findBySammenligningIdOrderByOrganisasjonsnummer(Long sammenligningId);
}
