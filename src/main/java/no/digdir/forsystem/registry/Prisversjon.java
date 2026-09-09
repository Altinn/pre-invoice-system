package no.digdir.forsystem.registry;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A yearly, versioned price list (docs/03 V1). Lifecycle UTKAST → AKTIV → ARKIVERT is enforced in
 * {@link PrisversjonService}. {@code status} is stored as text; see {@link PrisversjonStatus}.
 */
@Table("prisversjon")
public record Prisversjon(
        @Id Long id,
        String navn,
        LocalDate gyldigFra,
        LocalDate gyldigTil,
        String status) {

    public PrisversjonStatus statusEnum() {
        return PrisversjonStatus.valueOf(status);
    }
}
