package no.digdir.forsystem.registry;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface PrisversjonRepository extends ListCrudRepository<Prisversjon, Long> {

    List<Prisversjon> findAllByOrderByGyldigFraDesc();

    List<Prisversjon> findByStatus(String status);
}
