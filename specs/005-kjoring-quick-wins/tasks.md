---
description: "Task list for the run page quick wins"
---

# Tasks: A run page that says what to do next

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui.md](./contracts/ui.md)

**Tests**: Included. Write them first and watch them fail.

**Paths**: `M` = `src/main/java/no/digdir/forsystem`, `R` = `src/main/resources`,
`T` = `src/test/java/no/digdir/forsystem`

## Phase 1: Foundational (blocks the stories)

- [x] T001 [P] Write the unit test `T/web/FunngruppeTest.java`. It covers:
  - grouping by (alvorlighet, kode)
  - order: blocking first, then count descending, then code
  - `eksempel()` is the first message
  - empty input gives an empty list
- [x] T002 Create `M/web/Funngruppe.java` as research D2 describes: a record with a static `av(List<Kontrollfunn>)` and `antall()`, `blokkerende()`, `eksempel()`.
- [x] T003 [P] Add `periodeLabel()` to `M/billing/Fakturakjoring.java` (D1).
- [x] T004 Update `detalj()` in `M/web/FakturakjoringController.java`:
  - add `funngrupper`, `antallBlokkerende` and `antallAdvarsler`, and drop the `blokkerende`/`advarsler` lists
  - add `bruksdataavstemming`, only when the status is GENERERT (D8)

## Phase 2: US1 + US2 + US3 — panel, grouped findings, Filer (P1/P2)

All three restructure the same template, so they are one phase and one commit.

- [x] T005 Write `T/billing/KjoringNesteStegIT.java` (extends `BillingFixture`, MockMvc, all roles). Each case asserts that the panel appears in the HTML before `Sammendrag`.

  | Case | Setup | Assert |
  |---|---|---|
  | GENERERT, clean | no findings | panel heading "Kontroller og godkjenn"; "Ingen blokkerende funn"; Godkjenn not disabled |
  | GENERERT, MANGLER_KUNDE | usage for 3 unknown orgnrs | one `MANGLER_KUNDE` group row with count 3; Godkjenn disabled |
  | GODKJENT | approved run | "Eksporter til regnskap"; "Godkjent av" |
  | FORKASTET | discarded run | "Generer januar 2027 på nytt"; hidden `periode` value `2027-01` |
  | Filer | GENERERT run | `id="filer"` contains `fakturagrunnlag.xlsx` and `Avstemming bruksdata` |

- [x] T006 Restructure `R/templates/kjoring/detalj.html` to the order in contracts/ui.md.
  - the `neste-steg` panel per status, with the checklist and the existing action forms and dialogs moved into it
  - the findings table grouped as in the contract
  - the Filer section
  - the e-mail figures inside the EKSPORTERT panel only
  - the LG04 details after the invoices
- [x] T007 Add the `.neste-steg` and `.sjekkliste` styles to `R/static/css/forsystem.css`.
  - The checklist marks each item ok, feil, advarsel or ukjent with token colours, and writes the state in text too.
  - The panel's buttons get `ikke-utskrift`.

## Phase 3: US4 — the month, not a date (P2)

- [x] T008 [P] [US4] Use `kjoring.periodeLabel()` in titles and breadcrumbs in `R/templates/kjoring/{detalj,faktura,avstemming,lg04-kontroll}.html`, and in the period column of `R/templates/kjoring/liste.html` (a `.periode` class capitalises the first letter).
- [x] T009 [US4] Update existing tests that assert the old date label, if any (grep `Kjøring 2027-`).

## Phase 4: Polish

- [x] T010 Run quickstart.md in the browser, at desktop and 375 px, and in light and dark mode.
- [x] T011 Run `./mvnw verify`, set the spec status to Implemented, and commit `feat(web): run page leads with the next step, groups findings and files`.

## Implementation notes (2026-10-22)

- **T009.** No existing test asserted the old date label, so nothing changed.
- **T010.** Checked in the browser at 1280×800, in light and dark mode:
  - GENERERT, both clean and with 2 blocking findings plus 25 warnings inserted locally, which showed
    as 2 grouped rows
  - GODKJENT and EKSPORTERT, reached through the dialogs
  - the run list

  FORKASTET is covered by `KjoringNesteStegIT` only.
- **Fix found in the browser.** The e-mail figures inside the tinted panel needed the page
  background to stay visible.

## Dependencies

- T001 comes before T002.
- T002–T004 come before T005–T007.
- T008 can run beside T006 only if it avoids `detalj.html`, so do it after T006.
- Then T009, T010 and T011.
