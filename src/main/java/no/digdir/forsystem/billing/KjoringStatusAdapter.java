package no.digdir.forsystem.billing;

import java.time.LocalDate;
import java.util.Optional;

import no.digdir.forsystem.usage.KjoringStatusPort;
import org.springframework.stereotype.Component;

/** Billing-side implementation of the usage import's {@link KjoringStatusPort}. */
@Component
class KjoringStatusAdapter implements KjoringStatusPort {

    private static final String FORKASTET = "FORKASTET";

    private final FakturakjoringRepository kjoringer;
    private final KorrigeringskjoringEndringRepository valgteEndringer;

    KjoringStatusAdapter(FakturakjoringRepository kjoringer,
                         KorrigeringskjoringEndringRepository valgteEndringer) {
        this.kjoringer = kjoringer;
        this.valgteEndringer = valgteEndringer;
    }

    @Override
    public boolean finnesIkkeForkastetKjoring(LocalDate periode) {
        return kjoringer.existsByPeriodeAndStatusNot(periode, FORKASTET);
    }

    @Override
    public Optional<EffektivtGrunnlag> effektivtGrunnlag(LocalDate periode) {
        Optional<Fakturakjoring> siste = kjoringer.findFirstByPeriodeAndStatusOrderByVersjonDesc(
                periode, Kjoringstatus.EKSPORTERT.name());
        Optional<Fakturakjoring> rot = kjoringer.findFirstByPeriodeAndTypeAndStatusOrderByVersjonAsc(
                periode, Kjoringstype.ORDINAER.name(), Kjoringstatus.EKSPORTERT.name());
        if (siste.isEmpty() || rot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EffektivtGrunnlag(siste.get().id(), siste.get().versjon(),
                rot.get().bruksdataImportId(), valgteEndringer.eksporterteEndringIder(periode)));
    }
}
