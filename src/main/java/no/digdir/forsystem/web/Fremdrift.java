package no.digdir.forsystem.web;

import java.time.OffsetDateTime;
import java.util.List;

import no.digdir.forsystem.billing.Fakturakjoring;
import no.digdir.forsystem.common.Handling;
import no.digdir.forsystem.common.Hendelseslogg;

/**
 * How far a run has come, and who did each step when (specs/006 FR-006). Generation and approval come
 * from the run itself; export and discard only exist in the audit trail. Sending the e-mail to
 * regnskap is manual and never recorded, so that step is never done — it is current once exported.
 *
 * @param forkastet who discarded the run and when; {@code null} unless the run is FORKASTET
 */
public record Fremdrift(List<Steg> steg, Hendelse forkastet) {

    public enum Tilstand { FERDIG, NAA, SENERE }

    /** One step; {@code hvem}/{@code naar} are {@code null} when not done or not recorded. */
    public record Steg(String navn, Tilstand tilstand, String hvem, OffsetDateTime naar) {

        public boolean ferdig() {
            return tilstand == Tilstand.FERDIG;
        }

        public boolean naa() {
            return tilstand == Tilstand.NAA;
        }
    }

    public record Hendelse(String hvem, OffsetDateTime naar) {
    }

    /** {@code hendelser} are the run's audit rows, newest first. */
    public static Fremdrift av(Fakturakjoring kjoring, List<Hendelseslogg> hendelser) {
        String status = kjoring.status();
        boolean godkjent = kjoring.godkjentAt() != null || "GODKJENT".equals(status) || "EKSPORTERT".equals(status);
        boolean eksportert = "EKSPORTERT".equals(status);
        Hendelse eksport = eksportert ? siste(hendelser, Handling.EKSPORTERTE) : null;

        var generertSteg = new Steg("Generert", Tilstand.FERDIG, kjoring.generertAv(), kjoring.generertAt());
        var godkjentSteg = godkjent
                ? new Steg("Kontrollert og godkjent", Tilstand.FERDIG, kjoring.godkjentAv(), kjoring.godkjentAt())
                : new Steg("Kontrollert og godkjent", "GENERERT".equals(status) ? Tilstand.NAA : Tilstand.SENERE, null, null);
        var eksportSteg = eksportert
                ? new Steg("Eksportert", Tilstand.FERDIG, eksport == null ? null : eksport.hvem(), eksport == null ? null : eksport.naar())
                : new Steg("Eksportert", "GODKJENT".equals(status) ? Tilstand.NAA : Tilstand.SENERE, null, null);
        var sendtSteg = new Steg("Sendt til regnskap", eksportert ? Tilstand.NAA : Tilstand.SENERE, null, null);

        Hendelse forkastet = null;
        if ("FORKASTET".equals(status)) {
            forkastet = siste(hendelser, Handling.FORKASTET);
            if (forkastet == null) {
                forkastet = new Hendelse(null, null);
            }
        }
        return new Fremdrift(List.of(generertSteg, godkjentSteg, eksportSteg, sendtSteg), forkastet);
    }

    private static Hendelse siste(List<Hendelseslogg> hendelser, Handling handling) {
        return hendelser.stream()
                .filter(h -> handling.name().equals(h.handling()))
                .findFirst()
                .map(h -> new Hendelse(h.bruker(), h.tidspunkt()))
                .orElse(null);
    }
}
