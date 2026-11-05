# Tasks: Fakturareferanse control and bestillingsnummer fallback

**Input**: Design documents from `/specs/012-fakturareferanse/`

**Tests**: Included — the change alters what goes on the invoice.

## Phase 1: Setup

- [x] T001 Base the branch on `origin/main` (with specs/011) and point CLAUDE.md's Spec Kit section and `.specify/feature.json` at `specs/012-fakturareferanse`

## Phase 2: Foundational

- [x] T002 Add `MANGLER_FAKTURAREFERANSE` (ADVARSEL, definition) to `src/main/java/no/digdir/forsystem/billing/Kontrollkode.java`

## Phase 3: US2 — bestillingsnummer fallback (P1)

- [x] T003 [US2] `Referansepar.av` in `FakturakjoringService`: empty/blank bestillingsnummer → fakturareferanse at snapshot; registry untouched
- [x] T004 [US2] Customer page shows «— (fakturareferansen brukes)» in `src/main/resources/templates/kunde/detalj.html`

## Phase 4: US1 — finding and list (P1)

- [x] T005 [US1] One `MANGLER_FAKTURAREFERANSE` per invoice in `FakturakjoringService.bygg`, after `DELT_REFERANSE`
- [x] T006 [US1] `FakturaUtenReferanse` + `FakturakjoringService.utenFakturareferanse(kjoringId)`
- [x] T007 [US1] List `uten-fakturareferanse` in `EksportService.liste` and the controller's download path
- [x] T008 [US1] Section «Fakturaer uten fakturareferanse» in `src/main/resources/templates/kjoring/kontroller.html`

## Phase 5: Tests

- [x] T009 `src/test/java/no/digdir/forsystem/billing/FakturareferanseIT.java`: fallback in lines and LG04 header, blank value, kept value, no new split, split kept, finding per invoice, product row, two kundenumre, threshold/no-usage not listed, tab + downloads, empty state
- [x] T010 Give `KjoringNesteStegIT`'s «no findings» customer a reference

## Phase 6: Polish

- [x] T011 Demo: Oslo kommune with fakturareferanse only (`Demodata`)
- [x] T012 [P] docs/07 (OQ-15, new OQ-24), docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/09-beslutningslogg.md (B-20)
- [x] T013 `mvn verify`, commit with sign-off, push, open PR, browser test

## Dependencies

T002 before T005. T003 before T005 (the finding reads the snapshot). T006 before T007/T008.
