package no.digdir.forsystem.archive;

/**
 * Port for archived export artifacts (docs/02). MVP adapter writes to the local filesystem
 * ({@link LocalFileArchive}); a future Azure Blob adapter slots in behind this interface without the
 * core knowing where files land. No Azure SDK import may appear outside an adapter in this package.
 */
public interface FileArchive {

    /**
     * Store {@code innhold} under a logical {@code relativSti} (e.g. {@code "kjoring-3/lg04.txt"}).
     * Returns where it landed plus its SHA-256, for the {@code eksportfil} record.
     */
    Arkivert lagre(String relativSti, byte[] innhold);

    /** Read back a previously archived artifact by its stored location. */
    byte[] hent(String blobUrl);

    /** The result of archiving: the stored location (local path or blob URL) and content hash. */
    record Arkivert(String blobUrl, String sha256) {
    }
}
