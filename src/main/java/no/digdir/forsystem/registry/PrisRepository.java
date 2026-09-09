package no.digdir.forsystem.registry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface PrisRepository extends ListCrudRepository<Pris, Long> {

    List<Pris> findByPrisversjonId(Long prisversjonId);

    Optional<Pris> findByPrisversjonIdAndProduktId(Long prisversjonId, Long produktId);
}
