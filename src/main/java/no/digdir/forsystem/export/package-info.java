/**
 * Exports: the ported LG04 writer (Windows-1252, fixed-width), PDF renderer, and CSV/XLSX.
 * Øre conversion (×100, round half-up) happens only here. Writes via the {@code FileArchive}
 * port. Depends on {@code billing}, {@code archive}, {@code common}.
 */
package no.digdir.forsystem.export;
