---
description: "Task list for the run tabs"
---

# Tasks: Run page in tabs, with progress and invoice search

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui.md](./contracts/ui.md)

**Tests**: Included. Write them first.

**Paths**: `M` = `src/main/java/no/digdir/forsystem`, `R` = `src/main/resources`,
`T` = `src/test/java/no/digdir/forsystem`

## Phase 1: Foundational

- [x] T001 [P] Add `findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc(String, Long)` to `M/common/HendelsesloggRepository.java`.
- [x] T002 [P] Write `T/web/FremdriftTest.java`, one case per status as in the data-model table. Cover:
  - export who/when taken from an EKSPORTERTE event
  - a missing event giving a done step without who/when
  - a FORKASTET run, both before and after approval
- [x] T003 [P] Write `T/web/FakturautvalgTest.java`. Cover:
  - search on name, orgnr, kundenummer and order number, ignoring case
  - the three sorts
  - paging over 120 invoices, with a page out of range clamped
  - an empty result has 1 page
  - an unknown sort key falls back to order number
- [x] T004 Create `M/web/Fremdrift.java` (D4).
- [x] T005 Create `M/web/Fakturautvalg.java` (D5).
- [x] T006 [P] Create `M/web/Kjoringsrad.java` (D8).
- [x] T007 Create `R/templates/kjoring/fragmenter.html` with `hode(fane)`, `status(s)` and `fremdrift(f)`.
- [x] T008 In `T/web/TemplateKonvensjonTest.java`, skip templates named `fragmenter.html` (D9).
- [x] T009 Add the `.faner`, `.fane-antall`, `.fremdrift` and sortable-link header styles to `R/static/css/forsystem.css`.

## Phase 2: US1 + US2 — tabs and progress (P1)

- [x] T010 Write `T/billing/KjoringFanerIT.java`. Cover:
  - each tab returns 200 with `aria-current="page"` on its own tab link
  - the Kontroller badge is a danger badge with 3 for 3 MANGLER_KUNDE findings
  - `/avstemming` redirects to `/kontroller#bruksdata`
  - the Oversikt strip shows "Generert" done and "Kontrollert og godkjent" current
  - Historikk lists GENERERTE and GODKJENTE for the run, newest first
- [x] T011 In `M/web/FakturakjoringController.java`, add `fane(id, model)` and the routes `/{id}/kontroller`, `/{id}/fakturaer`, `/{id}/filer` and `/{id}/historikk`. Make `/{id}/avstemming` a redirect. Have `detalj` add `fremdrift`.
- [x] T012 Reduce `R/templates/kjoring/detalj.html` (Oversikt) to the header fragment, the stopped-export alert, the strip, the panel and the summary. Point the checklist links at `/kontroller#…`.
- [x] T013 [P] Create `R/templates/kjoring/kontroller.html` from the findings, the body of `avstemming.html` and the LG04 section. Delete `avstemming.html`.
- [x] T014 [P] Create `R/templates/kjoring/filer.html` and `R/templates/kjoring/historikk.html`.
- [x] T015 [P] Point the breadcrumbs of `R/templates/kjoring/{faktura,lg04-kontroll}.html` through their tab.

## Phase 3: US3 — find an invoice (P1)

- [x] T016 Add Fakturaer cases to `KjoringFanerIT`:
  - a search by orgnr gives 1 row and a count of 1
  - `sorter=belop` puts the largest first
  - the `<ds-pagination` element carries `data-total`
- [x] T017 Create `R/templates/kjoring/fakturaer.html` with the search form (debounced `hx-get` with `hx-select="#fakturaresultat"`, `hx-push-url`), the result line, sortable link headers, the table and `<ds-pagination data-href>`.

## Phase 4: US4 — run list (P2)

- [x] T018 Add a case to `KjoringFanerIT`: the list shows the status text "Venter på godkjenning", the invoice count, the sum and a blocking count.
- [x] T019 Have `liste()` build `Kjoringsrad` rows, and update `R/templates/kjoring/liste.html` with the new columns and the status fragment.

## Phase 5: Update existing tests

- [x] T020 In `T/billing/KjoringNesteStegIT.java`, read findings from `/kontroller` and Filer from `/filer`. In `T/billing/BruksdataavstemmingIT.java`, request `/kontroller`.

## Phase 6: Polish

- [x] T021 Run quickstart.md in the browser, at 1280×800 and 375 px, in light and dark mode.
- [x] T022 Run `./mvnw verify`, set the spec status to Implemented, and commit `docs(spec)` and `feat(web)`.

## Implementation notes (2026-10-22)

- **Pagination needs its links rendered.** `<ds-pagination>` doesn't create elements. It decorates
  the `<a>` elements it is given: the first is previous, the last is next, and the rest are page
  slots.
  - So the template renders those links on the server, with hrefs, page labels and `aria-current`.
    `Fakturautvalg.sider()` computes the same 7-slot window as the component, ellipsis included.
  - Paging therefore also works without JS.
  - Inactive previous/next links carry `aria-hidden="true"`, which the DS CSS hides.
- **Checked in the browser.** At 1280×800 and 375 px, light and dark:
  - Oversikt
  - Kontroller, with locally inserted findings
  - Fakturaer: live search while typing, sort by Sum while searching, and paging over 67 invoices
    inserted locally
  - Filer, Historikk and the run list

  At 375 px the tab bar scrolls within itself, and the page doesn't.
- **Tests that moved.** `KjoringNesteStegIT` (findings, Filer) and `BruksdataavstemmingIT` now open
  the tabs where that content lives.

## Dependencies

- T001–T009 come first. T002 and T003 come before T004 and T005.
- Phase 2 comes before Phases 3 and 4, which touch separate templates but the same controller, so
  run them one after the other.
- T020 comes after the moves, then the polish phase.
