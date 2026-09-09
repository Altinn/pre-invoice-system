package no.digdir.forsystem.usage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Port for a usage-data source (docs/02, docs/06 §1). The CSV upload is the MVP adapter and the
 * permanent fallback; a datavarehus adapter can slot in later behind the same interface, feeding the
 * identical validation pipeline. Sources yield raw rows; validation lives in {@link UsageImportService}.
 */
public interface UsageDataSource {

    Kilde kilde();

    /**
     * Read raw usage rows for the period. CSV parses {@code innhold}; a future DWH source would query
     * by period and ignore {@code innhold}.
     */
    List<RaaBruksrad> lesRader(no.digdir.forsystem.common.Periode periode, InputStream innhold) throws IOException;
}
