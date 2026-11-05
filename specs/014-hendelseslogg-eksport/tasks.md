# Tasks: The complete event log, filtered and exported

**Input**: Design documents from `/specs/014-hendelseslogg-eksport/`

**Tests**: Included.

## Phase 1: Setup

- [x] T001 Branch from `origin/main`; point CLAUDE.md's Spec Kit section and `.specify/feature.json` at `specs/014-hendelseslogg-eksport`

## Phase 2: Foundational

- [x] T002 `common/Detaljtekst.java`: jsonb → «nøkkel: verdi; …», `periode`
- [x] T003 `common/Hendelsesfilter.java`: fields, Oslo bounds, validity, query string
- [x] T004 `common/HendelsesloggSok.java`: page, all rows, distinct entities/actions

## Phase 3: US1 — page and filters (P1)

- [x] T005 [US1] `HendelsesloggController`: bind filters, page, model
- [x] T006 [US1] `templates/hendelseslogg/liste.html`: filter form, count, table (Oslo time, readable details), paging

## Phase 4: US2 — download (P1)

- [x] T007 [US2] `RegnearkEksport`: date-time cells, autofilter, frozen header
- [x] T008 [US2] `export/HendelsesloggEksport.java` + `GET /hendelseslogg.{xlsx,csv}`, logged

## Phase 5: Tests

- [x] T009 `DetaljtekstTest`
- [x] T010 `HendelsesloggIT`

## Phase 6: Polish

- [x] T011 [P] docs/brukerveiledning.md §9, docs/funksjonsoversikt.md F-AUD-02, docs/09 B-22
- [x] T012 `mvn verify`, browser check, commit with sign-off, push, PR

## Dependencies

T002–T004 before T005/T008. T007 before T008.
