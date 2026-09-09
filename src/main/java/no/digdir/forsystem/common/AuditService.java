package no.digdir.forsystem.common;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Writes {@code hendelseslogg} rows. Every registry/billing state change goes through here so the
 * audit trail is uniform (docs/02). The billing period, when relevant, belongs in {@code detaljer} —
 * it is never derived from the clock.
 */
@Service
public class AuditService {

    private final HendelsesloggRepository repository;
    private final BrukerContext brukerContext;
    private final ObjectMapper objectMapper;

    AuditService(HendelsesloggRepository repository, BrukerContext brukerContext, ObjectMapper objectMapper) {
        this.repository = repository;
        this.brukerContext = brukerContext;
        this.objectMapper = objectMapper;
    }

    /** Record a change to {@code entitet}#{@code entitetId} with structured {@code detaljer}. */
    public void logg(Handling handling, String entitet, Long entitetId, Map<String, ?> detaljer) {
        Jsonb json = detaljer == null ? null : Jsonb.of(serialiser(detaljer));
        repository.save(new Hendelseslogg(
                null,
                OffsetDateTime.now(),
                brukerContext.naavaerendeBruker(),
                handling.name(),
                entitet,
                entitetId,
                json));
    }

    public void logg(Handling handling, String entitet, Long entitetId) {
        logg(handling, entitet, entitetId, null);
    }

    private String serialiser(Map<String, ?> detaljer) {
        try {
            return objectMapper.writeValueAsString(detaljer);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Kunne ikke serialisere detaljer for hendelseslogg", e);
        }
    }
}
