package no.digdir.forsystem.usage;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataKundeRepository extends ListCrudRepository<BruksdataKunde, Long> {

    List<BruksdataKunde> findByImportIdOrderByOrganisasjonsnummer(Long importId);
}
