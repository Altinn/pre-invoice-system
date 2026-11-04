# Tasks: LG04 export in FEL's Altinn format

**Input**: Design documents from `/specs/008-lg04-altinn-format/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/lg04-altinn.md](./contracts/lg04-altinn.md)

**Tests**: Required. LG04 is contractual (constitution II): write each named test first and see it
fail before changing the code.

**Organization**: One phase per user story. `[P]` means a different file and no unfinished
dependency in the same phase. Tasks already done in commit `c344466` are checked.

## Phase 1: Setup

- [x] T001 Branch `feat/lg04-altinn-format` from `origin/main` and point `.specify/feature.json` at
  `specs/008-lg04-altinn-format`

## Phase 2: Foundational

No shared prerequisites: there is no schema change, and each story touches its own code path.

## Phase 3: User Story 1 — The LG04 file is what Unit4 expects (P1) 🎯 MVP — built in c344466

**Goal**: One order per invoice, header + product pairs, byte-identical to FEL's `test_3`.

**Independent Test**: `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`.

- [x] T002 [US1] Add FEL's sample, name anonymised, as `src/test/resources/lg04/altinn-test3.txt`;
  remove `linje1.txt`–`linje3.txt`
- [x] T003 [US1] Golden-file and offset tests in
  `src/test/java/no/digdir/forsystem/export/lg04/Lg04SkriverTest.java`
- [x] T004 [US1] Split `Lg04Ordre` into order + `Lg04Ordrelinje` in
  `src/main/java/no/digdir/forsystem/export/lg04/Lg04Ordre.java`
- [x] T005 [US1] Render header + numbered pairs, right-aligned amounts, product name, VALUE_1,
  sequence 8, header text in `src/main/java/no/digdir/forsystem/export/lg04/Lg04Skriver.java` and
  `src/main/java/no/digdir/forsystem/export/Lg04Eksport.java`
- [x] T006 [US1] Parse header + pairs independently in
  `src/main/java/no/digdir/forsystem/export/lg04/Lg04Leser.java`, tests in `Lg04LeserTest.java`
- [x] T007 [US1] Group orders per invoice (and per reference pair), buntnummer = export date, in
  `src/main/java/no/digdir/forsystem/export/EksportService.java`
- [x] T008 [US1] Område and buntnummer in the e-mail block:
  `src/main/java/no/digdir/forsystem/export/Lg04AvstemmingVisning.java`,
  `src/main/resources/templates/kjoring/detalj.html`
- [x] T009 [US1] Update `EksportIT`, `EksportAvstemmingIT`, `E2eScenarioIT` and
  `Lg04EksportUnitTest` to the new structure

## Phase 4: User Story 2 — Cost lines carry no false quantity (P1)

**Goal**: VALUE_1 blank without quantity, `0` for zero (FR-010).

**Independent Test**: An order with a cost line renders spaces at offsets 4224–4243.

- [x] T010 [US2] In `src/test/java/no/digdir/forsystem/export/lg04/Lg04SkriverTest.java`, replace
  `value1IsMinus100WithoutQuantity` with `value1IsBlankWithoutQuantity` (null antall → 20 spaces at
  4224) and add `value1IsZeroForZeroQuantity` (antall 0 → `0` right-aligned) and
  `value1RoundsToHundredths` (2.5 → `250`); run and see the first two fail
- [x] T011 [US2] In `src/main/java/no/digdir/forsystem/export/lg04/Lg04Skriver.java`, make
  `verdi1` return `""` for null antall and drop `VERDI_1_UTEN_ANTALL` and its TODO; the field is
  still padded to 20 (so blank stays blank); run T010 green and the golden test still green

## Phase 5: User Story 3 — The e-mail figures match what regnskap sees (P2)

**Goal**: Total incl. 25 % mva per invoice, "uavklart" labels gone (FR-011).

**Independent Test**: Two invoices of 10.01 kr → 20.02 eksl., 25.02 inkl. mva.

- [x] T012 [US3] In `src/test/java/no/digdir/forsystem/billing/EksportAvstemmingIT.java`, change
  `readerSeesSummaryEmailBlockAndReconciliation` to expect "eksl. mva" and "inkl. mva" lines and no
  "uavklart"; add an IT (same file) `inklMvaIsRoundedPerInvoice` with two customers each billed
  10.01 kr, asserting `sammendrag.sumInklMva()` = 25.02 (not 25.03); see both fail
- [x] T013 [US3] Add `sumInklMva` to `src/main/java/no/digdir/forsystem/billing/Kjoringssammendrag.java`
  (with a `MVA_FAKTOR = 1.25` constant, javadoc citing FEL 2026-11-04, and the OQ-11 sentence
  replaced), computed in `FakturakjoringService.sammendrag` as Σ round_half_up(faktura.sumBelop × 1.25, 2)
  in `src/main/java/no/digdir/forsystem/billing/FakturakjoringService.java`; fix any other
  constructor callers
- [x] T014 [US3] In `src/main/resources/templates/kjoring/detalj.html`, e-mail block: replace the
  "Samlet beløp … (eksl./inkl. mva uavklart)" line with "Samlet beløp eksl. mva: … kr" and
  "Samlet beløp inkl. mva: … kr"; summary table: "Sum beløp eksl. mva" plus a "Sum beløp inkl. mva"
  row; run T012 green

## Phase 6: User Story 4 — Different references on one invoice are visible (P2)

**Goal**: ADVARSEL `DELT_REFERANSE` at generation for an invoice that will be split (FR-009).

**Independent Test**: Customer with a general and a product-specific reference → warning, run
approvable, two orders in the file.

- [x] T015 [US4] In `src/test/java/no/digdir/forsystem/billing/EksportIT.java`, extend
  `anInvoiceWithTwoReferencesBecomesTwoOrders` to assert one `DELT_REFERANSE` ADVARSEL on the invoice
  whose message names `REF-MELDING`, and that a single-reference invoice (`approvedMultiLineRun`)
  has none; see it fail
- [x] T016 [US4] Add `DELT_REFERANSE` under ADVARSEL in
  `src/main/java/no/digdir/forsystem/billing/Kontrollkode.java` with a one-line javadoc
- [x] T017 [US4] In `src/main/java/no/digdir/forsystem/billing/FakturakjoringService.java`, after an
  invoice's lines are saved (next to the `NULLBELOP` check), collect the distinct
  (fakturareferanse, bestillingsnummer) pairs in line order; if more than one, add
  `advarsel(…, faktura.id(), DELT_REFERANSE, "Fakturaen har N ulike referanser (…) og blir N fakturaer i Unit4")`,
  showing a missing reference as «ingen referanse»; run T015 green
- [x] T018 [US4] Check the demo still generates and approves: Trøndelag now gets the warning
  (`src/main/java/no/digdir/forsystem/demo/Demodata.java` needs no change; note it in quickstart if
  behaviour differs)

## Phase 7: Polish & cross-cutting

- [x] T019 [P] Add `headerAndFooterTextAreCutAt120` to `Lg04SkriverTest.java` (a 130-char tekst and
  url: offsets 2509–2628 and 2629–2748 hold the first 120 chars, 2749 is a space); see it fail, then
  in `Lg04Skriver.felles` pad-and-cut both fields to 120
- [x] T020 [P] Update `docs/07-open-questions.md`: OQ-1 (no split by default — FR-009 decision, untested
  by FEL), OQ-2 (applikasjonsinfrastruktur → article 20009, unit KR in Unit4; regnskap's example uses
  other kontering — still Økonomi's call), OQ-11 (answered: 25 %, sum × 1.25 per invoice), OQ-22 (per
  organisasjonsnummer, chosen by Økonomi, adjustable), OQ-23 (unit code dropped for good; `-100` is
  FEL's error marker, we write blank; footer link not clickable in the PDF, field max 120)
- [x] T021 [P] Update `docs/09-beslutningslogg.md`: B-13 superseded (mva now shown), B-16 amended
  (split with warning, blank VALUE_1)
- [x] T022 [P] Update `docs/brukerveiledning.md` (summary and e-mail block show eksl./inkl. mva;
  `DELT_REFERANSE` row in the findings table) and `docs/funksjonsoversikt.md` (F-CTRL row for
  `DELT_REFERANSE`; F-EXP-04 wording)
- [x] T023 Run `mvn verify` (Colima env per quickstart) — all green
- [x] T024 Commit as `feat(export): …` with sign-off; tick the tasks in this file

## Dependencies

- US1 is done. US2, US3, US4 and T019 are independent of each other (different code paths; T011 and
  T019 both touch `Lg04Skriver.java`, so run them one after the other).
- Docs (T020–T022) after the code they describe; T023–T024 last.

## Parallel examples

- T010–T011 (writer) ‖ T012–T014 (summary) ‖ T015–T017 (generation finding).
- T020 ‖ T021 ‖ T022.

## Implementation strategy

US1 is the MVP and already on the branch. The follow-ups are each one test-first slice; finish all
four before the PR, so FEL's format, the warning and the mva figures land together and the December
dry-run tests the whole change.
