# Tasks: Invoice threshold (terskelverdi)

**Input**: Design documents from `/specs/011-terskelverdi/`

**Tests**: Included — the threshold decides who is invoiced.

## Phase 1: Setup

- [x] T001 Cut `feat/011-terskelverdi` from `main` and point CLAUDE.md's Spec Kit section at `specs/011-terskelverdi/plan.md`

## Phase 2: Foundational

- [x] T002 Add `src/main/resources/db/migration/V14__terskelverdi.sql`: `prisversjon.terskelverdi` (check ≥ 0, backfill 40.00) and table `faktura_under_terskel`
- [x] T003 [P] Add `terskelverdi` to `src/main/java/no/digdir/forsystem/registry/Prisversjon.java` (5-field constructor keeps NULL) and carry it through `PrisversjonService` (`opprettUtkast` 40.00, `aktiver`, `arkiver`)
- [x] T004 [P] Add `UNDER_TERSKEL` (ADVARSEL) to `src/main/java/no/digdir/forsystem/billing/Kontrollkode.java`
- [x] T005 [P] Create `FakturaUnderTerskel` + repository in `src/main/java/no/digdir/forsystem/billing/`

## Phase 3: US1 — small customers are not invoiced (P1)

- [x] T006 [US1] Apply the threshold per customer in `FakturakjoringService.bygg` (ordinary runs only): skip groups, store `faktura_under_terskel`, one `UNDER_TERSKEL` finding linked to the usage rows
- [x] T007 [US1] Delete `faktura_under_terskel` rows in `src/main/java/no/digdir/forsystem/billing/NullstillingService.java`

## Phase 4: US2 — see and export what is left out (P1)

- [x] T008 [US2] `FakturakjoringService.underTerskel(kjoringId)`; spreadsheet tables and logged downloads in `src/main/java/no/digdir/forsystem/export/EksportService.java`
- [x] T009 [US2] Endpoints `fakturaliste.{csv|xlsx}` and `under-terskel.{csv|xlsx}` in `FakturakjoringController`
- [x] T010 [US2] Section «Faktureres ikke (under terskelverdi)» and download links in `src/main/resources/templates/kjoring/fakturaer.html`

## Phase 5: US3 — threshold on the price version (P2)

- [x] T011 [US3] `PrisversjonService.settTerskelverdi` (UTKAST only, ≥ 0, audited) and `POST /prisversjoner/{id}/terskelverdi`
- [x] T012 [US3] Show/edit the threshold in `src/main/resources/templates/prisversjon/detalj.html`

## Phase 6: Tests

- [x] T013 `src/test/java/no/digdir/forsystem/billing/TerskelverdiIT.java`: 23,50/40,00/40,01, per-customer total over two kundenumre, finding + reconciliation, LG04 has only invoiced orders, correction exempt, list + downloads, price-version default/edit/lock, no threshold when NULL
- [x] T014 Update `MigrationIT` count and any test affected by the new default

## Phase 7: Polish

- [x] T015 [P] docs/07 OQ-22, docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/03-datamodel.md, docs/09-beslutningslogg.md
- [x] T016 `mvn verify`, commit with sign-off, push, open PR

## Dependencies

T002–T005 block the rest. US1 before US2 (the list needs stored rows). US3 is independent of US1/US2
except for the field from T003. Tests after implementation; docs any time.
