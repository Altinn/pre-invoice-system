package no.digdir.forsystem.billing;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A control finding for a run (docs/03 V4). {@code fakturaId} is null for run-level findings such
 * as usage whose orgnr has no customer. BLOKKERENDE findings prevent approval; an ADVARSEL prevents it
 * until a user has accepted it (specs/013): {@code akseptertAv}/{@code akseptertAt} are both null or both
 * set, and only ever set on an ADVARSEL.
 */
@Table("kontrollfunn")
public record Kontrollfunn(
        @Id Long id,
        Long kjoringId,
        Long fakturaId,
        String alvorlighet,
        String kode,
        String melding,
        String akseptertAv,
        OffsetDateTime akseptertAt) {

    /** A new, unaccepted finding (generation, tests). */
    public Kontrollfunn(Long id, Long kjoringId, Long fakturaId, String alvorlighet, String kode, String melding) {
        this(id, kjoringId, fakturaId, alvorlighet, kode, melding, null, null);
    }

    public boolean erAkseptert() {
        return akseptertAt != null;
    }

    public boolean blokkerende() {
        return Alvorlighet.BLOKKERENDE.name().equals(alvorlighet);
    }

    /** A warning that has not been accepted yet: it holds back approval. */
    public boolean aapenAdvarsel() {
        return Alvorlighet.ADVARSEL.name().equals(alvorlighet) && !erAkseptert();
    }

    public Kontrollfunn medAksept(String bruker, OffsetDateTime tidspunkt) {
        return new Kontrollfunn(id, kjoringId, fakturaId, alvorlighet, kode, melding, bruker, tidspunkt);
    }

    public Kontrollfunn utenAksept() {
        return new Kontrollfunn(id, kjoringId, fakturaId, alvorlighet, kode, melding, null, null);
    }
}
