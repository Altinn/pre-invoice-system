package no.digdir.forsystem.registry;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A yearly, versioned price list (docs/03 V1). Lifecycle UTKAST → AKTIV → ARKIVERT is enforced in
 * {@link PrisversjonService}. {@code status} is stored as text; see {@link PrisversjonStatus}.
 * {@code terskelverdi} is the invoice threshold in kroner ex. mva (specs/011): a customer whose total for
 * the month is not over it gets no invoice. Null means no threshold.
 */
@Table("prisversjon")
public record Prisversjon(
        @Id Long id,
        String navn,
        LocalDate gyldigFra,
        LocalDate gyldigTil,
        String status,
        BigDecimal terskelverdi) {

    /** Økonomi's decision for FinMod (kickoff list; FEL 2026-11-04, OQ-22): 40 kr ex. mva per customer per month. */
    public static final BigDecimal STANDARD_TERSKELVERDI = new BigDecimal("40.00");

    /** A version without a threshold — older fixtures and call sites. */
    public Prisversjon(Long id, String navn, LocalDate gyldigFra, LocalDate gyldigTil, String status) {
        this(id, navn, gyldigFra, gyldigTil, status, null);
    }

    public Prisversjon medStatus(PrisversjonStatus nyStatus) {
        return new Prisversjon(id, navn, gyldigFra, gyldigTil, nyStatus.name(), terskelverdi);
    }

    public PrisversjonStatus statusEnum() {
        return PrisversjonStatus.valueOf(status);
    }
}
