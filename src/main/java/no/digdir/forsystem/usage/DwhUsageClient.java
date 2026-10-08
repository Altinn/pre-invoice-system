package no.digdir.forsystem.usage;

import no.digdir.forsystem.common.Periode;

/**
 * Port to the datavarehus usage API (docs/06 §1, specs/001-dwh-usage-import). The adapter fetches one
 * period's raw response; parsing, aggregation and validation happen in the core so a staged import can
 * be re-validated from its archived payload without calling the DWH again.
 *
 * <p>Implementations MUST fail (throw {@link no.digdir.forsystem.common.Regelbrudd}) rather than
 * return a response they cannot vouch for being complete — under-counting usage under-invoices.
 */
public interface DwhUsageClient {

    DwhSvar hentMaaned(Periode periode);
}
