# Quickstart: validating Kontrollgrunnlag før godkjenning

## Prerequisites

- Java 21 and Docker (Colima locally: set `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
  as in the repo's dev notes)
- `./mvnw verify` green on the branch

## Automated

```bash
./mvnw verify
```

What must pass, in addition to the existing suite:

| Test | Proves |
|---|---|
| `ReimportEtterForkastIT` | generate → forkast → re-import → generate works; the old run's lines still resolve to their usage rows (D1) |
| `Lg04LeserTest` | reads the golden file with known totals; writer→reader round-trip; each structural error is detected (D4) |
| `EksportAvstemmingIT` | a successful export stores `lg04_avstemming` with matching figures; a forced mismatch leaves the run GODKJENT, writes no `eksportfil` and no archive file, and logs `EKSPORT_STOPPET` (FR-004/005, SC-002) |
| `BruksdataavstemmingIT` | per product/type sums; skipped rows linked to MANGLER_KUNDE / MANGLER_PRIS / MANGLER_KUNDENUMMER; invariant holds; inactive-but-expected products show 0 (FR-009..012, SC-003) |
| `FakturagrunnlagRegnearkIT` | pre-approval download from GENERERT, columns per data-model.md, numeric XLSX cells, Σ belop = run total, frozen name after a registry rename, no `eksportfil`, `LASTET_NED` logged, FORKASTET refused (FR-014..019, SC-005) |
| `WebKjoringRoleIT` (extended) | LESER can GET the new pages and downloads; LESER cannot POST export |
| Existing `Lg04SkriverTest` | unchanged, byte-identical (FR-022) |

## Manual walkthrough (demo profile)

```bash
docker compose up --build
```

1. **Kjøringer → Generer 2027-01.** The run page shows *Sammendrag* with antall fakturaer, antall
   LG04-ordrer (= antall linjer, "én ordre per fakturalinje (OQ-1)") and the sum labeled
   "eksl./inkl. mva uavklart".
2. **Last ned fakturagrunnlag (XLSX).** The file is `fakturagrunnlag-2027-01-GENERERT.xlsx`. In
   Excel, `=SUM()` of the belop column equals the summary's sum. The run is still GENERERT, and the
   hendelseslogg shows `LASTET_NED`.
3. **Avstemming bruksdata.** The demo data includes the special cases. Check that the per-product
   table balances, and that any row kept off the invoice is listed with its reason. If nothing was
   kept off, you see "All bruksdata er med i fakturagrunnlaget".
4. **Kunder → rename a customer**, then download the XLSX again. The name is unchanged (frozen at
   generation).
5. **Godkjenn → Eksporter.** The page shows "LG04 stemmer med fakturagrunnlaget" with the figures
   for the file and the run. The *Til e-post til regnskap* block copies periode, antall fakturaer
   and samlet beløp.
6. **Correction flow (T08)**:
   1. Upload a CSV for 2027-02 ([docs/csv-format.md](../../docs/csv-format.md)).
   2. Generate 2027-02, then Forkast it.
   3. Upload a corrected CSV for 2027-02 and generate again.

   This failed with a database error before D1. Now it succeeds, and the discarded run's
   *Avstemming bruksdata* still shows its original import.

The stopped-export path cannot be triggered from the UI with correct code. It is covered by
`EksportAvstemmingIT`, which injects a faulty order assembly.
