# Tasks: Import the datavarehus export with invoice flags

**Input**: `/specs/017-dwh-flagg-import/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)
**Tests**: included — the spec demands a golden fixture and exact totals (SC-001–003, SC-005).
**Format**: `- [ ] T### [P?] [US?] Description with file path`. `[P]` = different files, no dependency on an unfinished task.

Paths: `U` = `src/main/java/no/digdir/forsystem/usage`, `T` = `src/test/java/no/digdir/forsystem/usage`,
`R` = `src/main/resources`, `D` = `src/test/resources/dwh`.

## Phase 1: Setup

- [x] T001 Raise multipart limits to 100 MB (`spring.servlet.multipart.max-file-size`/`max-request-size`) in `R/application.yaml`, and make `web/GlobalExceptionHandler.java` answer `MaxUploadSizeExceededException` with a Norwegian message naming the limit (research R1)
- [x] T002 Build the reduced fixture `D/eksport-flagg-3m.csv` (~300 rows, selection rules in research R6) with a documented recipe in `D/README.md`; do not commit the 30 MB source
- [x] T003 [P] Add OQ-25 (flag/`regel` contract), OQ-26 (DPV rows), OQ-27 (repeated day rows) and update OQ-3, OQ-4, OQ-18 with the 2026-11-06 findings in `docs/07-open-questions.md`

## Phase 2: Foundational (blocks all stories)

- [x] T004 Extend `U/DwhRad.java` with nullable `flaggAltinn`, `flaggDpv`, `kildetabell`, `ressurs`, `regel`; fix all constructor callers (`DwhRespons`, tests) passing nulls
- [x] T005 [P] Create `U/DwhRadklasse.java` (enum BRUKT, UTENFOR_PERIODE, DPV, EKSKLUDERT) with the classifier from plan «Design outline» step 2, including legacy (null flag ⇒ BRUKT) and the month-before-flag ordering
- [x] T006 [P] Create `U/DwhFilrapport.java` and `U/DwhKildegruppe.java` records per data-model.md
- [x] T007 Rewrite `U/DwhCsv.java` as one streaming pass: recognise the new columns, `flagged` mode when `flag_inv_altinn` is in the header, lenient parsing of orgnr/quantity/amount on unused rows, `urn:altinn:resource:` normalisation, flag value ∉ {0,1} ⇒ rejected row; return a single result object (rows, report, customers) instead of separate `les`/`kunder` parses (research R2)
- [x] T008 Unit tests `T/DwhCsvFlaggTest.java` on the fixture: class counts, orgnr anomalies on unused rows tolerated, flag `2` rejected, urn normalisation, legacy header unchanged
- [x] T009 Keep `T/DwhCsvTest.java` and `T/DwhFilImportIT.java` green against the old fixtures (SC-005); adjust only call signatures

**Checkpoint**: reader produces classified rows; old files still import.

## Phase 3: User Story 1 — Correct basis from the flagged export (P1) 🎯 MVP

**Goal**: only flag-1 rows in the declared month become usage.
**Independent test**: fixture imported for 2026-10 ⇒ per (orgnr, product) usage equals the independent sum of flag-1 October rows.

- [x] T010 [US1] Update `U/DwhAggregering.java`: consume BRUKT rows only, NULL quantity on flagged volume rows = 0, zero rows add nothing, appinfra keeps `amount_nok`, emit `DwhKildegruppe`s that sum to the usage row
- [x] T011 [US1] Wire `U/DwhImportService.java#forhaandsvisFil` to the one-pass reader; refuse a flagged file with no BRUKT rows in the period via a blokkering (FR-012); restrict `manglendeKunder`/`opprettManglendeKunder` to BRUKT rows (spec clarification)
- [x] T012 [US1] Add `filrapport` and `kildegrupper` (nullable) to `U/Forhaandsvisning.java` and pass them through `U/UsageImportService.java#vurder`
- [x] T013 [P] [US1] Extend `T/DwhAggregeringTest.java`: flagged volume + appinfra amount, NULL quantity, repeated flagged day rows summed, unmapped name still one reject
- [x] T014 [US1] Integration test `T/DwhFlaggImportIT.java`: fixture for 2026-10 and 2026-09 — totals equal independent sums, flag-0 and DPV contribute 0 rows (SC-001/002), flagged row with bad orgnr rejected with line number, empty period refused, customers created only from used rows

**Checkpoint**: MVP — the real file can be imported month by month with correct totals.

## Phase 4: User Story 2 — See what was left out and why (P2)

**Goal**: preview shows used / other months / DPV / excluded with a balancing control sum.
**Independent test**: report counts equal counts computed independently from the fixture; sum equals total rows (SC-003).

- [x] T015 [US2] Fill `DwhFilrapport` in the reader/service: per-class counts, other-month counts per month, volume per product and class, excluded per verbatim `regel` reason, top excluded organisations incl. «ukjent orgnr»
- [x] T016 [US2] Render the report in `R/templates/bruksdata/forhandsvis.html` per contracts/upload-ui.md (including kontrollsum line); keep Thymeleaf conventions checked by `TemplateKonvensjonTest`
- [x] T017 [P] [US2] Test `T/DwhFilrapportTest.java`: counts, kontrollsum, unknown `regel` tokens passed through unchanged (FR-013)
- [x] T018 [US2] Add `brukte`/`utelatt`/`dpv`/`utenforPerioden` counts to the `IMPORTERTE` audit details in `U/UsageImportService.java` (FR-015) and assert it in `T/DwhFlaggImportIT.java`

## Phase 5: User Story 3 — Trace usage back to DWH sources (P2)

**Goal**: each imported usage row shows its source groups.
**Independent test**: groups for one (orgnr, product) sum to the usage figure; superseded imports keep theirs.

- [x] T019 [US3] Flyway `R/db/migration/V16__bruksdata_kilde.sql` per data-model.md (unique `nulls not distinct`, index, FKs)
- [x] T020 [P] [US3] Create `U/BruksdataKilde.java` and `U/BruksdataKildeRepository.java` (Spring Data JDBC, text[] for `regel`)
- [x] T021 [US3] Persist groups in `U/UsageImportService.java#importer` (also for staged DWH confirms); never delete on re-import
- [x] T022 [US3] Show groups per usage row in `web/BruksdataController.java` + `R/templates/bruksdata/detalj.html`; «ingen kildedetaljer» for imports without groups (contracts/upload-ui.md)
- [x] T023 [US3] Extend `T/DwhFlaggImportIT.java`: group sums equal usage rows for every row; re-import keeps old import's groups; archived file sha256 recorded

## Phase 6: User Story 4 — Unknown input fails loudly (P3)

**Independent test**: flag `2`/blank and a new product name each give a clear, line-numbered message.

- [x] T024 [US4] Make the reject messages precise (line, column, value) for bad/blank flag values in `U/DwhCsv.java`; reuse the existing «legg inn kobling under Produkter → Kildenavn» text for new product names
- [x] T025 [P] [US4] Tests in `T/DwhCsvFlaggTest.java` and `T/DwhFlaggImportIT.java` for both cases, plus header with `flag_inv_dpv` but no `flag_inv_altinn` ⇒ legacy behaviour with a warning (plan R/Design)

## Phase 7: Polish & cross-cutting

- [x] T026 [P] Update `docs/csv-format.md` (flagged export section, columns, row classes, one-period-per-upload) and `docs/03-datamodel.md` (`bruksdata_kilde`)
- [x] T027 [P] Update `docs/brukerveiledning.md` and `docs/runbook.md` (upload limit, preview breakdown, drill-down, DPV note) and the Bruksdata page help text if present
- [ ] T028 **BLOCKED** (the real 30 MB export is not in the repo; not measured with a substitute) Measure upload + preview on the real 30 MB file locally per quickstart.md step 2 (< 30 s, no OOM); if the session-held `byte[]` or heap is a problem, record the finding and adjust research R2
- [x] T029 Run `mvn verify` green; walk quickstart.md steps 1–5; fix anything found
- [ ] T030 Commit as conventional commit(s) on `017-dwh-flagg-import` (spec/plan artifacts separate from code if preferred), then open a PR

## Dependencies & order

- Phase 1 → Phase 2 → US1. T001 and T002 gate any real-file testing; T003 is independent.
- Within Phase 2: T004 → T005/T006 (parallel) → T007 → T008/T009.
- **US1 is the MVP.** US2 needs T012 (report plumbing) and T015; US3 needs T010 (groups) and T019 → T020 → T021; US2 and US3 do not depend on each other and can run in parallel after US1.
- US4 touches `DwhCsv` and tests only; run after Phase 2, any time.
- T026/T027 can start once behaviour is settled (after US1); T028–T030 last.

## Parallel examples

- Phase 2: T005 ‖ T006 after T004.
- After US1: T015–T018 (US2) ‖ T019–T023 (US3) — different files except `UsageImportService.java` (T018 vs T021: sequence those two).
- Polish: T026 ‖ T027.

## Implementation strategy

1. Land Phase 1–3 as the first PR (MVP: correct import, limits raised, fixture, OQs).
2. Add US2 and US3 (preview report, traceability) as one or two follow-up PRs; US4 hardening alongside.
3. Dry-run with the real file before the December rehearsal; keep OQ-25/26/27 open until the DWH answers.
