package no.digdir.forsystem.billing;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface FakturaUnderTerskelRepository extends ListCrudRepository<FakturaUnderTerskel, Long> {

    List<FakturaUnderTerskel> findByKjoringIdOrderByOrganisasjonsnummerAscKundenummerAsc(Long kjoringId);
}
