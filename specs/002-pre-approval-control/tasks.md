---
description: "Task list for Kontrollgrunnlag før godkjenning"
---

# Tasks: Kontrollgrunnlag før godkjenning

**Input**: Design documents from `specs/002-pre-approval-control/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui.md](./contracts/ui.md), [quickstart.md](./quickstart.md)

**Tests**: Included. The constitution requires `mvn verify` green per commit, and quickstart.md
names the ITs that prove each story. Write each story's tests first and watch them fail.

**Organization**: Phases follow the plan's delivery order. Each phase ends in one conventional
commit with `./mvnw verify` green.

**Paths**: `M` = `src/main/java/no/digdir/forsystem`, `R` = `src/main/resources`,
`T` = `src/test/java/no/digdir/forsystem`.

**Run tests locally**: `DOCKER_HOST=unix:///Users/adi/.colima/default/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw verify`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: US1 / US2 / US3 from spec.md

---

## Phase 1: Setup

**Purpose**: Shared vocabulary that later phases use.

- [x] T001 Add `EKSPORT_STOPPET` and `LASTET_NED` to the `Handling` enum, with a one-line Javadoc each, in `M/common/Handling.java`

---

## Phase 2: Foundational: keep superseded usage (research R1, D1; spec FR-023)

**Purpose**: Fix the confirmed defect where re-importing a period after a discarded run fails on
`fakturalinje_bruksdata_id_fkey`. It blocks BF1 T08, and US2 needs the run→import link.

**⚠️ CRITICAL**: US2 depends on this phase. US1 and US3 do not, but they ship after it anyway.

**Commit**: `fix(usage): keep superseded bruksdata so a discarded run's lines stay valid`

- [x] T002 Write the failing regression test in `T/billing/ReimportEtterForkastIT.java` (billing package, to reuse the package-private `BillingFixture`). Steps: import a CSV for JAN via `UsageImportService.forhaandsvis`+`importer`, then `generer`, `forkast`, re-import a changed CSV, and `generer` again. Assert:
  - the second run is GENERERT and uses the new rows
  - the first run's lines still resolve to their original `bruksdata` rows
  - the old import is AVVIST
  - `fakturakjoring.bruksdata_import_id` is set on both runs
- [x] T003 Create `R/db/migration/V8__bruksdata_per_import.sql` per data-model.md:
  - Drop `bruksdata_periode_organisasjonsnummer_produkt_id_type_key`. Verify the real name first, for example with `\d bruksdata` in a Testcontainers psql or a query on `pg_constraint`, and use `drop constraint if exists` only if the name is confirmed.
  - Add `bruksdata_unik_per_import unique (import_id, organisasjonsnummer, produkt_id, type)`.
  - Add `fakturakjoring.bruksdata_import_id bigint references bruksdata_import`, with the backfill `update` from lines.
  - Comment each block with its D-number.
- [x] T004 In `M/usage/UsageImportService.java` `importer()`: remove `bruksdata.deleteByPeriode(periode)` and keep the AVVIST marking. Update the comment to say rows of superseded imports are kept for traceability (constitution III).
- [x] T005 In `M/usage/UsageImportService.java`, add `public Optional<BruksdataImport> gjeldendeImport(Periode)`, which returns the latest (highest id) VALIDERT import for the period. Change `bruksdataFor(Periode)` to return that import's rows (`findByImportId`), or an empty list. Change the previous-period lookup in `avvikMotForrigeMaaned` the same way.
- [x] T006 Remove the now-unused `deleteByPeriode` from `M/usage/BruksdataRepository.java`. Check that nothing else calls it, including `findByPeriode` callers (`grep -rn findByPeriode src`).
- [x] T007 Add the `bruksdataImportId` component (after `prisversjonId`) to `M/billing/Fakturakjoring.java`. Update every `new Fakturakjoring(...)` call in `M/billing/FakturakjoringService.java` (generer, godkjenn, settEksportert, forkast) to carry it through. In `generer`, take it from `usage.gjeldendeImport(periode)`, and throw the existing "Ingen bruksdata" `Regelbrudd` when it is absent.
- [x] T008 Fix other constructors of `Fakturakjoring` in tests and demo (`grep -rn "new Fakturakjoring" src`), e.g. `M/demo/Demodata.java` and `T/billing/*`.
- [x] T009 Run `./mvnw verify`. T002 must now pass along with the whole suite, and `MigrationIT` must pass on V8. Commit.

**Checkpoint**: The T08 correction flow works end to end. This can ship alone.

---

## Phase 3: User Story 1: run summary and LG04 reconciliation (Priority: P1) 🎯 MVP

**Goal**: The run page shows the summary and the email block (FR-001..003, FR-008). The export
reads the LG04 back and reconciles it before archiving: a mismatch stops the export with the
FR-005a message, and a match is stored (FR-004..007).

**Independent Test**: Generate, approve and export the demo run. The summary matches the sums, and
the page shows "LG04 stemmer". A forced mismatch in a test leaves the run GODKJENT with no files and
an `EKSPORT_STOPPET` event.

**Commit**: `feat(export): reconcile LG04 against the run before archiving`

### Tests for User Story 1 (write first, see them fail)

- [x] T010 [P] [US1] Write `T/export/lg04/Lg04LeserTest.java` (plain unit test):
  - (a) reading the three golden lines `src/test/resources/lg04/linje1..3.txt` joined with `\n` gives 1 order and the golden amount in øre (read the expected value from `Lg04SkriverTest`'s fixture)
  - (b) round-trip: build 5 `Lg04Ordre` values, including a 0 amount and a negative amount, through `Lg04Eksport.bygg`; the reader gives count 5 and the exact øre sum
  - (c) one test per structural error: a short line, a line count not a multiple of 3, the wrong line-type order, mismatched order numbers within a triple, amount@212 ≠ amount@965, a non-numeric amount
- [x] T011 [P] [US1] Write `T/billing/EksportAvstemmingIT.java` (in package `billing`, alongside `EksportIT`):
  - (a) a successful export stores one `lg04_avstemming` row whose file and run figures are equal and match the run, with `sha256` = the LG04 `eksportfil.sha256`
  - (b) with a faulty order assembly injected (for example a `@TestConfiguration` that replaces `Lg04Eksport` with a subclass whose `bygg` drops the last order), `eksporter` throws `Lg04Avvik`; the run stays GODKJENT; there are 0 `eksportfil` rows and no new files under the archive root
  - (c) after (b), `loggStoppetEksport` has written an `EKSPORT_STOPPET` hendelseslogg row with both sides' figures
  - (d) `forventetAntallOrdrer` equals the number of lines
  - (e) `sammendrag` gives the right fakturaer, lines and sum
- [x] T012 [P] [US1] (Done inside `T/billing/EksportAvstemmingIT.java` instead, to share its Lg04Eksport spy context.) Web checks:
  - LESER GET `/kjoringer/{id}` shows "eksl./inkl. mva uavklart" and the summary figures
  - LESER GET `/kjoringer/{id}/lg04-kontroll` on an exported run is 200
  - a GODKJENNER POST `/kjoringer/{id}/eksporter` with the faulty assembly from T011 redirects to `/kjoringer/{id}` with a flash containing "Eksport stoppet" (a MockMvc `flash().attributeExists`)

### Implementation for User Story 1

- [x] T013 [US1] Create `R/db/migration/V9__lg04_avstemming.sql` with the `lg04_avstemming` table exactly as in data-model.md, including the equality `check`.
- [x] T014 [P] [US1] Create the entity `M/billing/Lg04Avstemming.java` (a `@Table("lg04_avstemming")` record) and `M/billing/Lg04AvstemmingRepository.java` with `Optional<Lg04Avstemming> findByKjoringId(Long)`.
- [x] T015 [P] [US1] Create `M/export/lg04/Lg04Leser.java`:
  - Signature: `public Lg04Innhold les(byte[] windows1252)`. Nested record `Lg04Innhold(int antallOrdrer, long sumOre, int antallLinjer, List<String> feil)` with `boolean gyldig()`.
  - Use its **own** offset constants: type@2493, amount@212..231 and @965..984, order number@3130..3144. Do not import or call `Lg04Skriver` (D4).
  - Decode as windows-1252, split on `\n`, and drop the single trailing empty element.
  - Every structural problem from T010(c) adds a Norwegian message to `feil`.
  - Parse amounts with `Long.parseLong(trim)`.
  - Class Javadoc explains why it is independent of the writer.
- [x] T016 [P] [US1] Create `M/export/Lg04Avvik.java`, a `Regelbrudd` subclass:
  - fields: periode, the file's `Lg04Innhold`, the run's `antallOrdrer`/`sumOre`
  - a `melding()` that produces the FR-005a text (spec.md) with amounts formatted as Norwegian kroner from øre (space thousands separator, comma decimals)
  - a `detaljer()` map for the audit log
- [x] T017 [P] [US1] Create the record `M/billing/Kjoringssammendrag.java` (periode, antallFakturaer, antallLinjer, sumBelop, sumFakturaer, `boolean konsistent()`). Add `public Kjoringssammendrag sammendrag(Long kjoringId)` to `M/billing/FakturakjoringService.java`, read-only and computed from the stored fakturaer and lines.
- [x] T018 [US1] Refactor `M/export/EksportService.java`:
  - extract the order assembly into `List<Lg04Ordre> ordrerFor(Fakturakjoring, List<Faktura>)`, behaviour unchanged
  - add `public int forventetAntallOrdrer(Long kjoringId)`, which delegates to it (D5)
  - add `static long ore(...)` as the run-side øre sum helper, reusing `ore(BigDecimal)`
- [x] T019 [US1] In `M/export/EksportService.java` `eksporter()`, reorder per D3:
  1. Build the LG04 bytes.
  2. Call `Lg04Leser.les`.
  3. Compute the run's order count and øre sum.
  4. If the file is invalid or the figures differ, throw `Lg04Avvik` **before** any `arkiver` call.
  5. Build and archive the PDF, CSV and XLSX, and archive the LG04 keeping the returned `Arkivert`.
  6. Assert `arkivert.sha256()` equals the SHA-256 of the reconciled bytes (else throw `Lg04Avvik` with a checksum message).
  7. Save `Lg04Avstemming`, linked to the LG04 `eksportfil` id (change `arkiver` to return the saved `Eksportfil`).
  8. Call `settEksportert`.
  9. Add `antallOrdrer`, `sumOre` and `lg04Sha256` to the `EKSPORTERTE` audit details.
- [x] T020 [US1] Add to `M/export/EksportService.java`:
  - `public void loggStoppetEksport(Long kjoringId, Lg04Avvik e)`. It is **not** `@Transactional`; it calls `audit.logg(EKSPORT_STOPPET, …)` (D7).
  - `public Optional<LgAvstemmingVisning> avstemmingFor(Long kjoringId)`, a view record with file/run figures, who, when, and a `kilde` of `LAGRET`, `BEREGNET_FRA_ARKIV` or `FEIL` with a reason. It reads the stored row, or for an EKSPORTERT run without a row it reads the archived LG04 via `FileArchive.hent`, checks the SHA-256 against `eksportfil.sha256`, and reconciles without persisting (D6). Any read error or hash mismatch gives `FEIL` with a reason (FR-007).
  - `public LgAvstemmingVisning kontrollerArkiv(Long kjoringId)`, which always re-reads and re-hashes.
- [x] T021 [US1] In `M/web/FakturakjoringController.java`:
  - `detalj` adds `sammendrag`, `antallOrdrer` (`eksport.forventetAntallOrdrer`) and `lg04Avstemming` (`eksport.avstemmingFor`) to the model
  - `eksporter` catches `Lg04Avvik`, calls `eksport.loggStoppetEksport`, and redirects to `/kjoringer/{id}` with `RedirectAttributes.addFlashAttribute("eksportStoppet", e.getMessage())`
  - add `GET /{id}/lg04-kontroll`, which renders `kjoring/lg04-kontroll.html` with `eksport.kontrollerArkiv(id)`
- [x] T022 [US1] Extend `R/templates/kjoring/detalj.html` per contracts/ui.md §1–4:
  - a *Sammendrag* `dl` with the "eksl./inkl. mva uavklart" label and the OQ-1 hint
  - a danger alert when `!sammendrag.konsistent()`
  - the *Til e-post til regnskap* block for GODKJENT/EKSPORTERT, with a `<pre id="epost">` and a "Kopier" button using `navigator.clipboard.writeText` (inline `<script>`, small, CSP permitting; otherwise `js/` static)
  - the *LG04-avstemming* section for EKSPORTERT (success, `BEREGNET_FRA_ARKIV` caption, or `FEIL` danger)
  - the `eksportStoppet` flash as a danger alert

  Use Designsystemet classes as the existing template does.
- [x] T023 [P] [US1] Create `R/templates/kjoring/lg04-kontroll.html`, which shows the re-check result with a link back to the run.
- [x] T024 [US1] Run `./mvnw verify`. T010–T012 must be green, and `Lg04SkriverTest` must be untouched and green (FR-022). Commit.

**Checkpoint**: BF1 T15 can be done on the run page, and a faulty LG04 can't leave forsystem.

---

## Phase 4: User Story 2: usage reconciliation view (Priority: P1)

**Goal**: `/kjoringer/{id}/avstemming` shows imported vs invoiced per product × type and lists every
skipped usage row with the finding that blocked it (FR-009..013). It depends on Phase 2.

**Independent Test**: Import usage with an unknown orgnr, a product with no price and a customer
with no kundenummer rule, then generate. The view lists exactly those rows with MANGLER_KUNDE /
MANGLER_PRIS / MANGLER_KUNDENUMMER, and the invariant holds.

**Commit**: `feat(billing): usage reconciliation view per run`

### Tests for User Story 2

- [ ] T025 [P] [US2] Write `T/billing/BruksdataavstemmingIT.java`:
  - (a) a clean run: every product/type row balances, `allFakturert()` is true, and the skipped list is empty
  - (b) an unknown orgnr, a missing price and a missing kundenummer rule: each skipped row is linked to the right `kontrollfunn` code
  - (c) a cost row (SMS_KOSTNAD) is compared on `belop` and a volume row on `antall`
  - (d) an active product with no usage appears with zeros
  - (e) INAKTIV_AVTALE rows count as invoiced
  - (f) a FORKASTET run, after re-import, still reconciles against its own import
  - (g) the invariant `importert = fakturert + ikkeFakturert` holds per row
- [ ] T026 [P] [US2] Extend `T/billing/WebKjoringRoleIT.java`: LESER GET `/kjoringer/{id}/avstemming` is 200 and contains "Avstemming bruksdata" and the FR-013 notice text.

### Implementation for User Story 2

- [ ] T027 [US2] Create `R/db/migration/V10__kontrollfunn_bruksdata.sql` with the link table and index per data-model.md.
- [ ] T028 [P] [US2] Create `M/billing/KontrollfunnBruksdata.java` (a record with a composite key; use a plain `JdbcTemplate`/`NamedParameterJdbcTemplate` repository class `KontrollfunnBruksdataRepository` with `lagre(List<…>)` and `findByKjoringId` via a join, because Spring Data JDBC does not do composite ids well).
- [ ] T029 [US2] In `M/billing/FakturakjoringService.java` `bygg()` (D2):
  - keep `Map<String, List<Long>>` of skipped bruksdata ids per finding key at each of the three `continue`s: orgnr → MANGLER_KUNDE, orgnr/kode → MANGLER_KUNDENUMMER, kode → MANGLER_PRIS
  - after `funn.saveAll`, match the saved findings back to their keys (keep a key alongside each `blokk(...)` you create, e.g. a parallel `Map<Kontrollfunn, String>` built before saving, or save those findings individually) and insert the links
- [ ] T030 [P] [US2] Create `M/billing/Bruksdataavstemming.java`, view records for `Rad(produktKode, produktNavn, type, importertRader, importertSum, fakturertLinjer, fakturertSum)` with `differanse()` and `balanserer(ikkeFakturertRader)`, plus `IkkeFakturert(bruksdataId, orgnr, produktKode, type, antall, belop, kode, melding)`, plus the top-level record with the import info, rows, the skipped list, `allFakturert()` and `invariantBrudd()`.
- [ ] T031 [US2] Create `M/billing/BruksdataavstemmingService.java` with `public Bruksdataavstemming for(Long kjoringId)`:
  - load the run's `bruksdataImportId`, then its rows via `usage.rader(importId)`, then the run's lines (by faktura), then the links
  - aggregate per product × type, including every active product, using `antall` for `erVolum()` types and `belop` for others
  - for runs with a null import id or no links, set reason "årsak ikke registrert (kjøring generert før avstemming fantes)"
  - read-only `@Transactional`
- [ ] T032 [US2] Add `GET /{id}/avstemming` to `M/web/FakturakjoringController.java`, rendering `kjoring/avstemming`.
- [ ] T033 [US2] Create `R/templates/kjoring/avstemming.html` per contracts/ui.md: the header with the import, the FR-013 info alert, the per-row table with differences highlighted, the success alert when everything is invoiced, the *Ikke fakturert* table, and a danger alert on invariant breach. Add the "Avstemming bruksdata" link in `R/templates/kjoring/detalj.html`.
- [ ] T034 [US2] Run `./mvnw verify` green. Commit.

**Checkpoint**: The software half of BF1 T06/T10 is in the app.

---

## Phase 5: User Story 3: spreadsheet before approval, with frozen customer name (Priority: P2)

**Goal**: CSV/XLSX download from GENERERT/GODKJENT/EKSPORTERT runs with the FR-015 columns, numeric
XLSX cells, a status in the file name and `LASTET_NED` audit. The customer name is snapshotted at
generation (FR-014..019, FR-015a).

**Independent Test**: Download the XLSX from a GENERERT run. Excel can sum the belop column to the
run total, nothing is recorded as an export file, and the event is logged. After a customer rename,
the name is unchanged.

**Commit**: `feat(export): fakturagrunnlag spreadsheet before approval, with frozen customer name`

### Tests for User Story 3

- [ ] T035 [P] [US3] Write `T/billing/FakturagrunnlagRegnearkIT.java`:
  - (a) GET `/kjoringer/{id}/fakturagrunnlag.xlsx` on a GENERERT run gives the file name `fakturagrunnlag-2027-01-GENERERT.xlsx`; the headers are in data-model.md order; there is one row per line; columns antall/enhetspris/belop are `CellType.NUMERIC`; Σ belop = `sammendrag.sumBelop`
  - (b) the CSV has the same header
  - (c) no `eksportfil` row is created, the status is unchanged, and `LASTET_NED` is logged with format and status
  - (d) on a FORKASTET run, the request is a 400 with the `Regelbrudd` text
  - (e) after renaming the customer in the registry, the downloaded name is still the original
  - (f) the archived CSV after export has the same header as the download (FR-019)
  - (g) a faktura with NULL `kundenavn` (simulated with an `update faktura set kundenavn = null`) shows the registry name + " (nåværende navn)"
- [ ] T036 [P] [US3] Extend `T/billing/WebKjoringRoleIT.java`: LESER can GET both downloads.

### Implementation for User Story 3

- [ ] T037 [US3] Create `R/db/migration/V11__faktura_kundenavn.sql`: `alter table faktura add column kundenavn text;` with a D9 comment.
- [ ] T038 [US3] Add the `kundenavn` component to `M/billing/Faktura.java` after `kundenummer`. Set it from `data.kunde.virksomhetsnavn()` in `M/billing/FakturakjoringService.java` `bygg()`. Fix all other `new Faktura(` call sites (`grep -rn "new Faktura(" src`).
- [ ] T039 [P] [US3] Change `M/export/RegnearkEksport.java` to typed cells: `csv(List<String>, List<List<Object>>)` and `xlsx(String, List<String>, List<List<Object>>)`. A `BigDecimal`/`Number` becomes a numeric cell (`setCellValue(double)` from `BigDecimal.doubleValue()`, with a numeric format that has 2 decimals for amounts and 4 for enhetspris), `null` becomes blank, and everything else is a string. CSV renders `BigDecimal` with `toPlainString()`.
- [ ] T040 [US3] Create `M/export/FakturagrunnlagRegneark.java`, the single column definition (data-model.md table, D8). Method `Tabell bygg(Fakturakjoring, List<Faktura>)` returns the header and typed rows ordered by (ordre, produkt, type). The type comes from the line's `bruksdata_id`, loaded in one query. The name follows the D9 display rule (snapshot, else registry name + " (nåværende navn)").
- [ ] T041 [US3] In `M/export/EksportService.java`:
  - replace `byggRader`/`hode` with `FakturagrunnlagRegneark` for the archived CSV/XLSX
  - add `public Nedlasting regneark(Long kjoringId, String format)`, which refuses FORKASTET with `Regelbrudd("Forkastede kjøringer kan ikke lastes ned som fakturagrunnlag")`, builds the bytes, logs `LASTET_NED` (periode, format, status) and returns the file name `fakturagrunnlag-<periode>-<STATUS>.<ext>` + content type + bytes
  - in the PDF HTML, add a `Kunde: <name>` line using the D9 rule
- [ ] T042 [US3] Add `GET /{id}/fakturagrunnlag.xlsx` and `GET /{id}/fakturagrunnlag.csv` to `M/web/FakturakjoringController.java`, which return `eksport.regneark(...)` as an attachment.
- [ ] T043 [US3] In `R/templates/kjoring/detalj.html`, add the download links ("Last ned fakturagrunnlag (XLSX, for Excel)" / "(CSV)") for non-FORKASTET runs, and a **Kunde** column in the faktura table (D9 rule; expose a helper or precomputed name map from the controller).
- [ ] T044 [US3] Run `./mvnw verify` green. Commit.

**Checkpoint**: BF1 T01 can be sampled in Excel before approval.

---

## Phase 6: Polish and documentation

**Commit**: `docs: document pre-approval control basis (002)`

- [ ] T045 [P] Update `docs/funksjonsoversikt.md`:
  - new rows F-EXP-14 (LG04 read-back reconciliation), F-EXP-15 (pre-approval spreadsheet), F-GEN-15 (run summary + email block), F-GEN-16 (usage reconciliation view) and F-GEN-17 (customer name snapshot)
  - amend F-USG-12 (re-import keeps superseded rows), F-EXP-09/10 (typed columns) and F-AUD-01 (new handlings)
  - bump the test count
- [ ] T046 [P] Update `docs/11-testplan-bf1.md`:
  - mark C-01, C-06 and C-14 done with feature IDs
  - change T01 → ✅, T06 → 🟡 (software part done, DWH part P-1), T10 → 🟡 (C-09 still open) and T15 → ✅
  - T08: the defect is fixed
  - recompute the tally
- [ ] T047 [P] Update `docs/03-datamodel.md` with V8–V11 (constraint change, `bruksdata_import_id`, `kundenavn`, `kontrollfunn_bruksdata`, `lg04_avstemming`).
- [ ] T048 [P] Update `docs/brukerveiledning.md` (Norwegian): the run summary, the email block, LG04-avstemming, Avstemming bruksdata and the spreadsheet download.
- [ ] T049 [P] Update `docs/runbook.md`: what to do on "Eksport stoppet" (contact the forsystem team, nothing to undo, re-export after the fix), and the T08 correction flow (forkast → re-import → generate).
- [ ] T050 Walk through quickstart.md manually against `docker compose up --build` (demo profile), steps 1–6. Fix anything found. Run `./mvnw verify` and commit.

---

## Dependencies and execution order

```text
T001 ─┐
      ├─► Phase 2 (T002–T009) ─► US1 (T010–T024) ─► US2 (T025–T034) ─► US3 (T035–T044) ─► Polish
```

- **Phase 2 → US2** is a hard dependency (`bruksdata_import_id`, rows kept).
- **US1 and US3** do not technically depend on Phase 2 or on each other. They are serialized only
  because the migrations are numbered in commit order (V8 → V11) and each commit must migrate
  cleanly.
- Within a story: tests first (they fail) → migration → entities/records → services → controller →
  templates → verify and commit.
- Shared files touched by several stories (serialize these): `FakturakjoringService.java`,
  `EksportService.java`, `FakturakjoringController.java`, `detalj.html` and `WebKjoringRoleIT.java`.

## Parallel opportunities

- **US1**: T010, T011 and T012 (tests) together; then T014, T015, T016 and T017 (new files) together.
- **US2**: T025 and T026 together; then T028 and T030 together.
- **US3**: T035 and T036 together; T039 alongside T037/T038.
- **Polish**: T045–T049 all in parallel.

## Implementation strategy

1. **Phase 2 first.** It is the bug fix that unblocks BF1 T08. It is releasable on its own and
   should be merged and deployed to tt02 even if the rest waits.
2. **MVP = Phase 2 + US1**: Økonomi gets T15 (totals + LG04 check) and the email figures, which is
   the most expensive error class closed.
3. **+ US2**: T06/T10 can be checked in the app.
4. **+ US3**: T01 can be sampled in Excel.
5. Each phase is a PR-sized commit with green CI. Stop at any checkpoint and the system is
   consistent.
