package no.digdir.forsystem.usage;

import java.util.List;

import no.digdir.forsystem.common.Periode;

/**
 * The staging report shown before an import is committed (docs/04 Phase 2): the valid rows,
 * per-product sums, the rejects, any blocking reasons (e.g. an existing run) and non-blocking
 * warnings. Import is all-or-nothing: it may only be committed when there are no rejects and no
 * blockers; warnings never block.
 *
 * <p>For a datavarehus import {@code raadata} carries the exact response the rows were built from, and
 * {@code stagetImportId} is set when the preview was rebuilt from a staged ({@code MOTTATT}) import.
 */
public record Forhaandsvisning(
        Periode periode,
        String filnavn,
        Kilde kilde,
        List<Bruksdata> gyldige,
        List<AvvistRad> avviste,
        List<ProduktSum> summer,
        List<String> blokkeringer,
        List<String> advarsler,
        Raadata raadata,
        Long stagetImportId) {

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
