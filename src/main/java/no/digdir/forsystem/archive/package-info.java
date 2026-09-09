/**
 * The {@code FileArchive} port plus adapters (local filesystem in dev/test, Azure Blob in prod).
 * No Azure SDK import may appear outside an adapter class in this package. Depends on {@code common}.
 */
package no.digdir.forsystem.archive;
