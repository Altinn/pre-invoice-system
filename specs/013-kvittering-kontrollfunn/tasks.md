# Tasks: Acknowledge control findings and export the Kontroller lists

**Input**: Design documents from `/specs/013-kvittering-kontrollfunn/`

**Tests**: Included — the change gates approval.

## Phase 1: Setup

- [x] T001 Branch from `origin/main`; point CLAUDE.md's Spec Kit section and `.specify/feature.json` at `specs/013-kvittering-kontrollfunn`

## Phase 2: Foundational

- [x] T002 `src/main/resources/db/migration/V15__kontrollfunn_aksept.sql` (columns + two checks)
- [x] T003 `Kontrollfunn`: `akseptertAv`, `akseptertAt`, compat constructor, `erAkseptert()`, `medAksept`, `utenAksept`
- [x] T004 `KontrollfunnRepository`: `countByKjoringIdAndAlvorlighetAndAkseptertAtIsNull`, `findByKjoringIdAndKodeOrderById`
- [x] T005 `Handling`: `AKSEPTERTE_FUNN`, `ANGRET_AKSEPT`

## Phase 3: US1 + US2 — accept and gate (P1)

- [x] T006 [US1] `FakturakjoringService.settAksept(kjoringId, funnId, boolean)` and `settAkseptForKode(kjoringId, kode, boolean)`: lock, GENERERT only, ADVARSEL only, own run only, audit when something changed
- [x] T007 [US2] `FakturakjoringService.godkjenn`: refuse while an ADVARSEL is unaccepted; `antallAapneAdvarsler`
- [x] T008 [US1] `SecurityConfig`: `POST /kjoringer/*/kontrollfunn/**` → FORVALTER or GODKJENNER
- [x] T009 [US1] `Funnstatus` (counts + wording), `Funngruppe` holds findings, `Kjoringsrad.antallAksepterte`
- [x] T010 [US1] Controller: two POST endpoints, open-warning counts in `fane()`, run list, Oversikt model
- [x] T011 [US1] `kontroller.html`: «Funn akseptert» column, per-finding checkboxes in «Vis alle», severity text
- [x] T012 [US1] `forsystem.js`: `[data-auto-send]` submits its form on change; `<noscript>` button
- [x] T013 [US2] `detalj.html`: checklist lines, disabled «Godkjenn» with reason; `fragmenter.html` badge; `liste.html` tags

## Phase 4: US3 — downloads (P2)

- [x] T014 [US3] `EksportService.liste`: `kontrollfunn`, `avstemming-bruksdata`, `ikke-fakturert`; controller path regex
- [x] T015 [US3] Download links on the Kontroller tab

## Phase 5: Tests

- [x] T016 `BillingFixture.aksepterAlleAdvarsler(kjoringId)`; existing ITs that approve runs with warnings accept them first
- [x] T017 `KontrollfunnAkseptIT` (service, web, roles, gate, regeneration, correction, wording, DB check)
- [x] T018 `KontrollerEksportIT` (three lists × two formats, audit, correction refusal); `FunngruppeTest` updated

## Phase 6: Polish

- [x] T019 [P] docs/03 (V15), docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/09 (B-21)
- [x] T020 `mvn verify`, browser check with demo data, commit with sign-off, push, PR

## Dependencies

T002–T005 before T006. T006/T007 before T010. T009 before T011/T013. T014 before T015.
