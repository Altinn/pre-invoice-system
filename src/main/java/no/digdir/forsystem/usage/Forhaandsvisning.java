package no.digdir.forsystem.usage;

import java.util.List;

import no.digdir.forsystem.common.Periode;

/**
 * The staging report shown before an import is committed (docs/04 Phase 2): the valid rows,
 * per-product sums, the rejects with line numbers, and any blocking reasons (e.g. an existing run).
 * Import is all-or-nothing: it may only be committed when there are no rejects and no blockers.
 */
public record Forhaandsvisning(
        Periode periode,
        String filnavn,
        List<Bruksdata> gyldige,
        List<AvvistRad> avviste,
        List<ProduktSum> summer,
        List<String> blokkeringer) {

    public boolean kanImporteres() {
        return avviste.isEmpty() && blokkeringer.isEmpty() && !gyldige.isEmpty();
    }

    public int antallGyldige() {
        return gyldige.size();
    }

    public int antallAvviste() {
        return avviste.size();
    }
}
