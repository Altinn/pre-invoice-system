package no.digdir.forsystem.usage;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface BruksdataKildeRepository extends ListCrudRepository<BruksdataKilde, Long> {

    List<BruksdataKilde> findByImportIdOrderByOrganisasjonsnummerAscProduktIdAscTypeAscKildetabellAscRessursAsc(
            Long importId);
}
