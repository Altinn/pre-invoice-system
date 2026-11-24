package no.digdir.forsystem.usage;

import java.util.List;

import no.digdir.forsystem.common.Periode;

/**
 * The staging report shown before an import is committed (docs/04 Phase 2): the valid rows,
 * per-product sums, the rejects, any blocking reasons (e.g. an existing run) and non-blocking
 * warnings. Import is all-or-nothing: it may only be committed when there are no rejects and no
 * blockers; warnings never block.
 *
 * <p>A flagged datavarehus export (specs/017) also carries {@code filrapport}, what became of every row,
 * and {@code kildegrupper}, the grouped source summary of the usage rows.
 *
 * <p>{@code kunder} are the customer values the source delivered per organisation (specs/018), stored with
 * the import and put on the invoice at generation. Missing kundenummer and conflicting values are shown as
 * facts about the file; they never block the import, only the run.
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
        Long stagetImportId,
        DwhFilrapport filrapport,
        List<DwhKildegruppe> kildegrupper,
        List<Kundedata> kunder) {

    /** Without file report or source groups (CSV in forsystem's format, API, tests). */
    public Forhaandsvisning(Periode periode, String filnavn, Kilde kilde, List<Bruksdata> gyldige,
                            List<AvvistRad> avviste, List<ProduktSum> summer, List<String> blokkeringer,
                            List<String> advarsler, Raadata raadata, Long stagetImportId) {
        this(periode, filnavn, kilde, gyldige, avviste, summer, blokkeringer, advarsler, raadata,
                stagetImportId, null, List.of(), List.of());
    }

    /** The same preview with what the flagged export's reader and aggregation found (specs/017). */
    Forhaandsvisning medFil(DwhFilrapport rapport, List<DwhKildegruppe> grupper) {
        return new Forhaandsvisning(periode, filnavn, kilde, gyldige, avviste, summer, blokkeringer, advarsler,
                raadata, stagetImportId, rapport, grupper, kunder);
    }

    /** The same preview with the customer values of the organisations in its valid rows (specs/018). */
    public Forhaandsvisning medKunder(List<Kundedata> alle) {
        java.util.Set<String> orgnr = new java.util.HashSet<>();
        gyldige.forEach(b -> orgnr.add(b.organisasjonsnummer()));
        List<Kundedata> utvalg = alle.stream().filter(k -> orgnr.contains(k.organisasjonsnummer())).toList();
        return new Forhaandsvisning(periode, filnavn, kilde, gyldige, avviste, summer, blokkeringer, advarsler,
                raadata, stagetImportId, filrapport, kildegrupper, utvalg);
    }

    /** Organisations whose rows disagree on kundenummer, fakturareferanse or bestillingsnummer. */
    public List<Kundedata> motstridendeKunder() {
        return kunder.stream().filter(Kundedata::harMotstridende).toList();
    }

    /** Organisations whose fakturareferanse or bestillingsnummer is longer than LG04 has room for. */
    public List<Kundedata> forLangeReferanser() {
        return kunder.stream().filter(Kundedata::harForLangReferanse).toList();
    }

    /** Organisations with usage but no kundenummer: they get MANGLER_KUNDENUMMER at generation. */
    public List<Kundedata> utenKundenummer() {
        java.util.Set<String> med = new java.util.HashSet<>();
        kunder.stream().filter(k -> k.kundenummer() != null).forEach(k -> med.add(k.organisasjonsnummer()));
        java.util.Set<String> uten = new java.util.LinkedHashSet<>();
        gyldige.stream().map(Bruksdata::organisasjonsnummer).filter(o -> !med.contains(o)).forEach(uten::add);
        java.util.Map<String, Kundedata> perOrgnr = new java.util.HashMap<>();
        kunder.forEach(k -> perOrgnr.put(k.organisasjonsnummer(), k));
        return uten.stream().map(o -> perOrgnr.getOrDefault(o, new Kundedata(o, null, null, null, null, List.of())))
                .toList();
    }

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
