# Tasks: Clearer texts, labels and number format

**Input**: Design documents from `/specs/010-tekst-og-tallformat/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui-tekster.md](./contracts/ui-tekster.md)

**Tests**: Write each named test first and see it fail. The page ITs assert the wording in
`contracts/ui-tekster.md`.

**Organization**: One phase per user story. `[P]` means a different file with no unfinished
dependency in the same phase.

## Phase 1: Setup

- [x] T001 Confirm branch `feat/010-tekst-og-tallformat` and point `.specify/feature.json` at
  `specs/010-tekst-og-tallformat`. Done when the spec commit `00ac2db` is on the branch.

## Phase 2: Foundational (blocks US1, US2, US4, US5)

- [x] T002 Write `src/test/java/no/digdir/forsystem/web/TallformatTest.java` and see it fail. Cases:
  - `belop`: 1234567.891 → «1 234 567,89»; -1234.5 → «-1 234,50»; 0 → «0,00»; null → «—».
  - `antall`: 1234567 → «1 234 567».
  - `mengde`: 1234 → «1 234,00».
  - `pris`: 0.0375 → «0,0375»; 1250 → «1 250,00»; 1.2500 → «1,25».
- [x] T003 Create `src/main/java/no/digdir/forsystem/web/Tallformat.java` as
  `@Component("tall")` with `belop`, `antall`, `mengde` and `pris`, per research R1. Run T002 green.

## Phase 3: User Story 1 — Amounts read the same way everywhere (P1) 🎯 MVP

**Goal**: FR-001–FR-003.

**Independent Test**: Every amount and count on the listed pages uses `@tall`, and every amount
label says eksl. mva.

- [x] T004 [US1] Extend `src/test/java/no/digdir/forsystem/billing/KjoringFanerIT.java` and see it fail:
  - a run whose total is over 1 000 kr renders its sums as «1 234,56»-style text on Oversikt,
    Fakturaer, Kontroller and one Faktura;
  - each amount header contains «eksl. mva».
- [x] T005 [P] [US1] Replace every `#numbers.formatDecimal(...)` and raw number output in the
  `src/main/resources/templates/kjoring/` pages with `@tall` (amounts `belop`, counts `antall`,
  quantities `mengde`, prices `pris`):
  - `liste.html`, `detalj.html`, `fakturaer.html`, `faktura.html`;
  - `kontroller.html`, `lg04-kontroll.html`, `fragmenter.html`.

  Then add «eksl. mva» to every amount label in those pages.
- [x] T006 [P] [US1] Do the same in the `bruksdata/` pages (`detalj.html`, `sammenligning.html`,
  `liste.html`) and in `prisversjon/detalj.html`. Prices use `pris`.
- [x] T007 [US1] Run T004 green.

## Phase 4: User Story 2 — Importing usage is safe and leads to the next step (P1)

**Goal**: FR-005–FR-007.

**Independent Test**: Preview shows names, warnings and pointers per `contracts/ui-tekster.md`.

- [x] T008 [US2] Add a page IT and see it fail. Use `KjoringNesteStegIT` or a new
  `BruksdataTeksterIT` in `src/test/java/no/digdir/forsystem/billing/`. It checks:
  - the preview shows «Melding» rather than «melding»;
  - an unknown code is shown as «… (ukjent produkt)»;
  - the preview has the Bekreft import warning;
  - Bruksdata has the Hent warning and the next-step link to `/kjoringer`;
  - Kjøringer has the top text with a link to `/bruksdata`;
  - preview numbers read «1 234,00».
- [x] T009 [US2] In `src/main/java/no/digdir/forsystem/web/BruksdataController.java`, add the
  product-code → name map to the model in `visForhaandsvisning`.
- [x] T010 [US2] In `src/main/resources/templates/bruksdata/forhandsvis.html`:
  - name column and `@tall` for the numbers;
  - «Sum beløp (eksl. mva)»;
  - the Bekreft import warning right before the button.
- [x] T011 [US2] Add the Hent warning and the next-step pointer to
  `src/main/resources/templates/bruksdata/liste.html`.
- [x] T012 [US2] Add the top text to `src/main/resources/templates/kjoring/liste.html`. Run T008 green.

## Phase 5: User Story 3 — A reviewer understands every control finding (P1)

**Goal**: FR-008, FR-009.

**Independent Test**: Every `Kontrollkode` has a severity and a definition, and the Kontroller tab
shows them all.

- [x] T013 [US3] Write `src/test/java/no/digdir/forsystem/billing/KontrollkodeTest.java` and see it
  fail. It asserts:
  - every constant has a non-blank `definisjon()` and a non-null `alvorlighet()`;
  - the five BLOKKERENDE and five ADVARSEL codes match the current split.
- [x] T014 [US3] Add `alvorlighet` and `definisjon` to
  `src/main/java/no/digdir/forsystem/billing/Kontrollkode.java`, with one sentence per code describing
  current behaviour. Make the finding helpers in
  `src/main/java/no/digdir/forsystem/billing/FakturakjoringService.java` refuse a mismatched severity.
  Run T013 green.
- [x] T015 [US3] Extend `KjoringFanerIT` and see it fail. The Kontroller tab must:
  - head its column «Beskrivelse», not «Eksempel»;
  - have both explanations, each listing every code's definition;
  - do this even with no findings.
- [x] T016 [US3] Pass `Kontrollkode.values()` to the Kontroller model in
  `src/main/java/no/digdir/forsystem/web/FakturakjoringController.java`. Then, in
  `src/main/resources/templates/kjoring/kontroller.html`:
  - add the two `<details>` explanations, linked from the Alvorlighet and Kode headers;
  - rename the column.

  Run T015 green.

## Phase 6: User Story 4 — Avstemming bruksdata says what it checks and who imported (P2)

**Goal**: FR-010, FR-011.

- [x] T017 [US4] Extend `src/test/java/no/digdir/forsystem/billing/BruksdataavstemmingIT.java` to
  assert the intro text and the four column names, and see it fail.
- [x] T018 [US4] Update the Avstemming bruksdata section of
  `src/main/resources/templates/kjoring/kontroller.html`. Run T017 green.
- [x] T019 [P] [US4] Write `src/test/java/no/digdir/forsystem/web/OidcBrukernavnTest.java`. It loads
  `application-prod.yaml` and asserts that
  `spring.security.oauth2.client.provider.entra.user-name-attribute` is `preferred_username`. See it
  fail.
- [x] T020 [US4] Add the property to `src/main/resources/application-prod.yaml`. Run T019 green.

## Phase 7: User Story 5 — Products and invoice lines are easier to read (P2)

**Goal**: FR-004, FR-012.

- [x] T021 [P] [US5] Update `src/test/java/no/digdir/forsystem/web/DimensjonsnavnTest.java`:
  «Dim1» with no name, and «Dim1 Koststed» with one. See it fail.
- [x] T022 [US5] Change the label format in `src/main/java/no/digdir/forsystem/web/Dimensjonsnavn.java`,
  and set the defaults `Koststed`, `Prosjekt/formål` and `Kappost` in
  `src/main/resources/application.yaml`. These values come from Økonomi, and env vars still override
  them. Run T021 green.
- [x] T023 [US5] Extend `KjoringFanerIT`, and see it fail. One invoice's page must:
  - show `kundenavn` and the reference pair once;
  - have no «Ref.» column;
  - end with a sum row equal to `faktura.sumBelop()`.
- [x] T024 [US5] In `FakturakjoringController.faktura`, add the distinct reference pairs. In
  `src/main/resources/templates/kjoring/faktura.html`:
  - customer name and references in the header;
  - drop the Ref. column;
  - add a `<tfoot>` sum row.

  Run T023 green.

## Phase 8: Polish & cross-cutting

- [x] T025 Update `docs/brukerveiledning.md`:
  - number format;
  - the explanations on Kontroller;
  - the import warning;
  - Avstemming bruksdata wording;
  - invoice page.
- [x] T026 Update `docs/funksjonsoversikt.md` for the affected rows, and docs/07 OQ-2 (the dimension
  names given by Økonomi).
- [x] T027 Run `mvn verify` (Colima env per quickstart). It must be all green, with the LG04 golden
  file unchanged.
- [x] T028 Tick the tasks in this file. Commit as `feat(ui): …` with sign-off.

## Dependencies

- T002–T003 come before US1, US2 (preview numbers), US4 and US5 (sum row).
- US3 is independent of `Tallformat`.
- T016 and T018 both edit `kontroller.html`, and T005 also touches it. Run T005 → T016 → T018 in
  that order.
- T005 and T024 both edit `faktura.html`. Run T005 first.

## Parallel examples

- T005 ‖ T006 (different template folders).
- T013–T014 (enum) ‖ T019–T020 (config) ‖ T021–T022 (dimensions).

## Implementation strategy

Foundational and US1 make the biggest visible difference. US2 and US3 are pure template work on top.
Ship all five together so Økonomi re-tests one build in the December dry-run.
