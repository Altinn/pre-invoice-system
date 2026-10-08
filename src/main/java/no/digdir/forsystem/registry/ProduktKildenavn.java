package no.digdir.forsystem.registry;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps a usage source's own product name (e.g. the datavarehus's {@code "Varsling e-post"}) to a
 * forsystem product and usage type. Maintained by FORVALTER; never seeded, because which name means
 * which product is an open business question (OQ-18).
 */
@Table("produkt_kildenavn")
public record ProduktKildenavn(
        @Id Long id,
        String kilde,
        String kildenavn,
        Long produktId,
        String type) {
}
