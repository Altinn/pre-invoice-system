# Research: Kontrollgrunnlag før godkjenning

Findings from reading the code on 2026-10-12, plus decisions D1–D9. Every decision follows from the
spec ([spec.md](./spec.md)) and the constitution. No business values are invented.

## Findings

### R1 — Re-importing usage after a run was generated crashes (defect, confirmed)

`UsageImportService.importer` implements "re-import replaces the period" by
`delete from bruksdata where periode = ?`. Every `fakturalinje.bruksdata_id` references
`bruksdata` (V4, no `on delete`). After **any** run has been generated for a period, even one that
was later FORKASTET, re-importing that period fails:

```
ERROR: update or delete on table "bruksdata" violates foreign key constraint
"fakturalinje_bruksdata_id_fkey" on table "fakturalinje"
```

This was reproduced with a throwaway IT (generate → forkast → re-import), which was then deleted.
The user sees the generic error page.

**Why it matters here**
- It breaks BF1 **T08**, the correction flow (fix in source → forkast → re-import → regenerate).
  That is the exact sequence Økonomi will test.
- The usage reconciliation view (US2) must still work for a FORKASTET run, but deleting the run's
  usage rows would erase what it reconciles against.
- Deleting history also contradicts constitution III ("history is never rewritten").

No existing test covers forkast → re-import, which is why the defect went unnoticed.

### R2 — Control findings are not linked to usage rows

`kontrollfunn` rows are aggregated: one MANGLER_KUNDE per orgnr, one MANGLER_PRIS per product, one
MANGLER_KUNDENUMMER per orgnr/product. They carry `kjoring_id` and an optional `faktura_id`, but
nothing points at the usage rows that were skipped. The generator (`FakturakjoringService.bygg`)
knows exactly which row hit which rule (`continue` at three places). That knowledge is thrown away
today.

### R3 — The run does not record which usage import it was built from

`generer` reads `usage.bruksdataFor(periode)`, which is all rows for the period. Because of R1 that
was the only import's rows. Once R1 is fixed, several imports' rows can exist for one period, so
the run must remember its import.

### R4 — LG04 amounts are written twice per order

`Lg04Skriver.renderLinje2` writes the øre amount at offset 212 (unpadded, followed by
`BELOP_SATT='1'` at 232) and again at offset 965 (20 wide, right-padded). Line type is at offset
2493 (`'0'` header, `'1'` amount/trailer). The order number is at 3130 (15 wide) on all three lines.
The sequence number is at 3314 on the trailer only. Each order is exactly three lines, 4324
characters each, `\n`-terminated, in Windows-1252.

### R5 — Export archives files before it changes status; nothing is checked

`EksportService.eksporter` builds the LG04, PDF, CSV and XLSX, archives each through `FileArchive`
(which writes to disk/blob immediately, outside the DB transaction), then sets EKSPORTERT. A check
that fails *after* archiving would leave orphan files behind, even though the DB rolls back.

### R6 — Spreadsheet cells are all text

`RegnearkEksport.xlsx` writes every value with `setCellValue(String)`. Excel shows amounts as text,
so Økonomi cannot sum the column (spec FR-016). The CSV uses `,` as separator and `.` as decimal,
which is fine for machines but Norwegian Excel misreads it. XLSX is the file for people.

### R7 — Failed actions are not audited

`AuditService.logg` runs inside the caller's transaction. When `eksporter` throws, the audit row
rolls back with everything else. A "stopped export" event therefore has to be written *after* the
rollback, outside that transaction.

### R8 — Customer name is not on the faktura

`faktura` snapshots `kundenummer`, `tilleggstekst` and `fakturamottaker_orgnr`, but not the name.
`kunde.organisasjonsnummer` cannot be edited (only navn, fakturamottaker and avtalestatus can), so
a live lookup of orgnr is safe. A live lookup of the name is not.

## Decisions

### D1 — Keep superseded usage rows; scope everything to the import (fixes R1, R3)

- Re-import no longer deletes rows. The old import is still marked AVVIST, as today.
- The uniqueness key moves from `(periode, orgnr, produkt, type)` to
  `(import_id, orgnr, produkt, type)`.
- `bruksdataFor(periode)` returns the rows of the period's single **VALIDERT** import. The previous
  period lookup for the ±50 % warning does the same.
- `fakturakjoring.bruksdata_import_id` records the import a run was built from. It is backfilled
  for existing runs from their lines.

**Rationale**: this is the smallest change that makes the correction flow work and keeps every
line traceable (constitution III).

**Rejected alternatives**:
- `on delete set null` on `fakturalinje.bruksdata_id` breaks traceability.
- Deleting the FORKASTET run's lines erases history.
- Blocking re-import after any run makes T08 impossible.

**Delivered as its own commit** (`fix(usage): …`) before the feature commits, with an IT that
reproduces R1.

### D2 — Link each skipped usage row to the finding that blocked it (fixes R2)

New table `kontrollfunn_bruksdata (kontrollfunn_id, bruksdata_id)`. During `bygg`, every
`continue` records the row id under the finding's key (orgnr / orgnr+produkt / produkt). After
`funn.saveAll`, the links are inserted.

**Rationale**: the reason becomes structural and is not parsed from the message text. The
reconciliation view is then a plain join.

**Rejected alternative**: re-deriving the reason at view time by re-running the rules. The
registry may have changed since generation, so the derived reason could be wrong.

Runs generated before this feature have no links. Their skipped rows show "årsak ikke registrert
(kjøring generert før avstemming)".

### D3 — Reconcile the LG04 in memory, before anything is archived (fixes R5)

Order of operations in `eksporter`:
1. Build the LG04 bytes.
2. Read them back with `Lg04Leser`.
3. Compare with the run.
4. On mismatch, throw `Lg04Avvik` before any `FileArchive.lagre`, so no orphan files are left.
5. Archive everything as today.
6. Check that the archive's returned SHA-256 equals the SHA-256 of the bytes that were reconciled.
7. Persist an `lg04_avstemming` row.
8. Set EKSPORTERT.

**Rationale**: "exactly as it will be delivered" (FR-004) is guaranteed by the checksum in step 6.
Nothing is written anywhere until the file has been proven correct.

### D4 — `Lg04Leser` is an independent reader, not a reuse of `Lg04Skriver`

It is a new class in `export.lg04` with its **own** offset constants, taken from the reference
format (R4) and pinned by the golden test file. It validates:
- every line is 4324 characters
- the line count is a multiple of 3
- each order is a `'0'`, `'1'`, `'1'` triple with the same order number on all three lines
- the amounts at 212 and 965 agree

It returns order count, øre sum and a list of structural errors. Any structural error counts as a
mismatch.

**Rationale**: a reader that shares the writer's code would share its bugs. Independence is the
point of the check.

**Tests**:
- read the existing golden file and assert its known totals
- writer→reader round-trip over generated orders
- one test per structural error class

### D5 — Run figures come from the same order assembly the export uses

- Run øre sum = Σ over lines of `ore(belop)`, using the same rounding as the export.
- Run order count = the size of the order list from the export's own assembly. Today that is one
  order per line (OQ-1). The pre-export summary uses the same function
  (`EksportService.forventetAntallOrdrer`), so the summary can never assume orders = lines.
- Σ `faktura.sum_belop` × 100 must equal the line øre sum. If it doesn't, that is reported as a
  defect too.

### D6 — Persist the reconciliation; recompute only for runs exported before this feature

`lg04_avstemming` is 1:1 with the run's LG04 `eksportfil`. It holds both sides' figures, the
SHA-256, and who and when. The run page reads the row, which is cheap.

For an EKSPORTERT run with no row (exported before this feature), the page reads the archived
file and reconciles on the fly. It does not persist the result, because a GET must not write. Only
a handful of tt02 test runs are affected.

A read-only "Kontroller arkivert LG04 på nytt" action re-reads and re-hashes the archive on demand
for any exported run (FR-007). It is not run on every page view, because a production file can be
tens of MB.

### D7 — Stopped exports are audited after the rollback (fixes R7)

`Lg04Avvik` is a subclass of `Regelbrudd` that carries both sets of figures.
`FakturakjoringController.eksporter` catches it, calls `EksportService.loggStoppetEksport`, and
redirects to the run page with a flash message containing the FR-005a text. The log call runs
outside the rolled-back transaction, so the row survives.

New `Handling` values: `EKSPORT_STOPPET` and `LASTET_NED`. `hendelseslogg.handling` is free text,
so no migration is needed.

**Rationale**: the user stays on the run page (FR-005a) instead of the generic error page, and the
failure is still on record.

### D8 — One spreadsheet definition, typed cells (fixes R6)

A new `FakturagrunnlagRegneark` in `export` builds the header and rows for a run. It is used both
by the export (archived CSV/XLSX) and by the new download endpoint, so FR-019 holds by
construction.

`RegnearkEksport` takes typed cells: `BigDecimal` becomes a numeric XLSX cell, and CSV renders it
with `toPlainString`.

CSV stays comma-separated with `.` decimals, so it is not a breaking change. The UI labels XLSX as
"for Excel".

### D9 — Freeze the customer name at generation (fixes R8, spec FR-015a)

`faktura.kundenavn` is a nullable text column, set from `kunde.virksomhetsnavn` in `bygg`. Where it
is NULL (pre-feature rows), views fall back to the current registry name, suffixed
"(nåværende navn)".

The name is shown in the spreadsheet, the run page's faktura table and the PDF heading. It does not
go into the LG04, so the golden files are unchanged.

## Not needed / ruled out

- **No new role and no security changes.** New GETs are LESER by the existing rule. The export POST
  stays GODKJENNER.
- **No VAT.** The label "eksl./inkl. mva uavklart" only (OQ-11).
- **No change to `Lg04Skriver`.** The golden tests stay byte-identical (constitution II).
