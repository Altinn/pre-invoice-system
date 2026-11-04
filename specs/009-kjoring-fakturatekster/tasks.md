# Tasks: Invoice texts per invoicing run

**Input**: Design documents from `/specs/009-kjoring-fakturatekster/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui.md, quickstart.md

**Tests**: Requested by the task brief — defaults, edits, length validation, approved-run lock, and
the golden file unchanged.

## Phase 1: Setup

- [x] T001 Cut `feat/009-kjoring-fakturatekster` from `feat/lg04-altinn-format` and point CLAUDE.md's Spec Kit section at `specs/009-kjoring-fakturatekster/plan.md`

## Phase 2: Foundational (blocks every story)

- [x] T002 Add `V13__kjoring_fakturatekster.sql` in `src/main/resources/db/migration/`: columns `topptekst`, `bunntekst`, `var_referanse`; length checks 120/120/25; check that GENERERT/GODKJENT runs have all three; backfill GENERERT/GODKJENT runs with the defaults (Norwegian month via `case`)
- [x] T003 [P] Create `Fakturatekster` value object in `src/main/java/no/digdir/forsystem/billing/Fakturatekster.java`: defaults (`standard(Periode)`), LG04 limits, advisory printed limits `TODO(OQ-23)`, `normalisert()`, `valider()`
- [x] T004 Add the three fields to `Fakturakjoring` (`tekster()`, `medTekster()`, carried by `medStatus`) in `src/main/java/no/digdir/forsystem/billing/Fakturakjoring.java`
- [x] T005 Add `finnForOppdatering(id)` (`select … for update`) to `src/main/java/no/digdir/forsystem/billing/FakturakjoringRepository.java`

## Phase 3: User Story 1 — defaults reach the file (P1)

**Independent test**: generate, approve, export; every LG04 line carries the default texts.

- [x] T006 [US1] Set `Fakturatekster.standard(periode)` in `generer` and `genererKorrigering` in `src/main/java/no/digdir/forsystem/billing/FakturakjoringService.java`
- [x] T007 [US1] Rename `Lg04Ordre.tekst/url` to `topptekst/bunntekst` in `src/main/java/no/digdir/forsystem/export/lg04/Lg04Ordre.java`; pad/cut `responsible` fields to 25 in `src/main/java/no/digdir/forsystem/export/lg04/Lg04Skriver.java`
- [x] T008 [US1] Build orders from the run's texts in `src/main/java/no/digdir/forsystem/export/EksportService.java`; refuse export of a run without texts; delete `src/main/java/no/digdir/forsystem/export/EksportKonfig.java` and `forsystem.eksport.*` from `src/test/resources/application-test.yaml`
- [x] T009 [P] [US1] Update `EksportAvstemmingIT` for the renamed record accessors in `src/test/java/no/digdir/forsystem/billing/EksportAvstemmingIT.java`
- [x] T010 [P] [US1] Writer test: `responsible` is cut at 25 and never spills into 3289; golden test unchanged, in `src/test/java/no/digdir/forsystem/export/lg04/Lg04SkriverTest.java`

## Phase 4: User Story 2 — Økonomi edits a text (P1)

**Independent test**: edit the footer; page, history and file show the new text.

- [x] T011 [US2] Add `endreTekster(kjoringId, Fakturatekster)` to `FakturakjoringService`: lock row, validate, store, audit `ENDRET` with only changed fields (none on no-op)
- [x] T012 [US2] Add `POST /kjoringer/{id}/tekster` in `src/main/java/no/digdir/forsystem/web/FakturakjoringController.java`: refusals flash `teksterFeil` + `innsendteTekster`, success flashes `teksterLagret`
- [x] T013 [US2] Add the «Tekster på fakturaen» section to `src/main/resources/templates/kjoring/detalj.html` per contracts/ui.md

## Phase 5: User Story 3 — over-long or unprintable texts are refused (P1)

**Independent test**: 121/121/26 characters, line break, emoji, blank are refused; texts unchanged.

- [x] T014 [P] [US3] Unit tests for `Fakturatekster` in `src/test/java/no/digdir/forsystem/billing/FakturateksterTest.java`
- [x] T015 [US3] Show LG04 limit and advisory printed limit next to each field, and a warning above the printed limit, in `detalj.html`

## Phase 6: User Story 4 — texts locked after approval (P1)

**Independent test**: approve/export/discard, then submit a change; refused, nothing logged.

- [x] T016 [US4] `godkjenn` reads the run via `finnForOppdatering`; `endreTekster` refuses non-GENERERT runs in `FakturakjoringService.java`
- [x] T017 [US4] Read-only rendering for non-GENERERT runs (and «ikke lagret» for NULL) in `detalj.html`

## Phase 7: Integration tests

- [x] T018 [US1–US4] `src/test/java/no/digdir/forsystem/billing/KjoringFakturateksterIT.java`: defaults (ordinary + correction), edit + audit, no-op, refusal leaves texts, lock after approve/export/discard, export writes edited texts on every line, web form roles and error round-trip

## Phase 8: Polish

- [x] T019 [P] Update docs/07 OQ-23 (built per run, printed limit still open) and OQ-2 (responsible no longer from config) in `docs/07-open-questions.md`
- [x] T020 [P] Describe the texts in `docs/brukerveiledning.md`; update `docs/funksjonsoversikt.md` (F-EXP-07), `docs/03-datamodel.md` and `docs/09-beslutningslogg.md`
- [x] T021 Run `mvn verify` (Colima env vars per quickstart.md) and commit once with sign-off

## Dependencies

T002–T005 block everything. US1 (T006–T010) before US2 (the edit relies on the stored fields being
exported). US3 and US4 extend US2's service method and template. T018 after T006–T017. Docs (T019,
T020) can run alongside any story.

## Parallel opportunities

T003 alongside T002; T009/T010 alongside T008; T014 alongside T011–T013; T019/T020 any time.

## Implementation strategy

All four stories are P1 and small; they ship together in one commit (task brief). MVP order if cut:
US1 (defaults reach the file) → US4 (lock) → US2/US3 (editing with validation).
