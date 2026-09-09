package no.digdir.forsystem.registry;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An Altinn product (docs/03 V1). Products are the temporary master maintained here. Kontering
 * ({@code artikkelId}, {@code konto}, {@code dim1/2/4}) is nullable until Økonomi answers OQ-2;
 * generation (Phase 3) blocks on NULL kontering.
 */
@Table("produkt")
public record Produkt(
        @Id Long id,
        String kode,
        String navn,
        Integer artikkelId,
        String konto,
        @Column("dim_1") String dim1,
        @Column("dim_2") String dim2,
        @Column("dim_4") String dim4,
        String enhet,
        boolean aktiv) {
}
