package no.digdir.forsystem.billing;

import java.time.LocalDate;

import no.digdir.forsystem.usage.KjoringStatusPort;
import org.springframework.stereotype.Component;

/** Billing-side implementation of the usage import's {@link KjoringStatusPort}. */
@Component
class KjoringStatusAdapter implements KjoringStatusPort {

    private static final String FORKASTET = "FORKASTET";

    private final FakturakjoringRepository kjoringer;

    KjoringStatusAdapter(FakturakjoringRepository kjoringer) {
        this.kjoringer = kjoringer;
    }

    @Override
    public boolean finnesIkkeForkastetKjoring(LocalDate periode) {
        return kjoringer.existsByPeriodeAndStatusNot(periode, FORKASTET);
    }
}
