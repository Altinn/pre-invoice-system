# Feature Specification: Kontrollgrunnlag før godkjenning (pre-approval control basis)

**Feature Branch**: `002-pre-approval-control`

**Created**: 2026-10-12

**Status**: Implemented (2026-10-12)

**Input**: User description: "Kontrollgrunnlag før godkjenning — the three Must items from
docs/11-testplan-bf1.md that let Økonomi control a fakturakjøring before approving it and reconcile
it after export: C-14 run summary + LG04 reconciliation, C-06 usage reconciliation view, C-01
pre-approval CSV/XLSX."

## Context

The business side's BF1 test plan ([docs/11-testplan-bf1.md](../../docs/11-testplan-bf1.md))
describes how Økonomi will control each monthly fakturakjøring: sample the lines in a spreadsheet
(T01), confirm that no usage was left out by mistake (T06, T10), and check that the count and total
amount in the LG04 file match the approved fakturagrunnlag (T15). It also covers the covering email
to regnskap, which states periode, antall fakturaer and samlet beløp (OQ-8).

Forsystem already guarantees most of this structurally. Every imported usage row becomes an
invoice line or a blocking finding, and the LG04 is written from the approved lines. But nothing
*shows* it. Today:

- the run page has no total count or amount
- nothing compares the written LG04 file against the run
- there is no view comparing imported usage with invoiced usage
- the spreadsheet of the lines appears only after approval, at export

This feature makes those controls visible, so the controls in T01, T06, T10 and T15 can be done in
the app instead of by hand.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the run's totals and trust that the LG04 matches them (Priority: P1)

An approver from Økonomi opens a fakturakjøring. At the top of the page they see the run summary:
periode, antall fakturaer, the number of LG04 orders the export will produce, and the total amount.
The total is labeled "eksl./inkl. mva uavklart", because whether amounts include VAT is still open
(OQ-11). After export, the same page shows the result of an automatic reconciliation of the written
LG04 file against the run, with orders and amount on each side. The approver copies the figures
into the covering email to regnskap.

**Why this priority**: T15 is a control Økonomi must do every month, and today it can't be done in
the app. The covering email needs exactly these figures (OQ-8). A mismatch between the file and the
approved basis is the most expensive error forsystem could make, because it reaches customers'
invoices.

**Independent Test**: Generate, approve and export a run from the demo data. Check that the
summary figures equal the sums of the run's fakturaer and lines. Check that the reconciliation shows
a match. Then corrupt a copy of an LG04 file in a test and check that the reconciliation reports
the difference.

**Acceptance Scenarios**:

1. **Given** a GENERERT run with 4 fakturaer and 9 invoice lines totalling 12 345,67, **When** a
   user opens the run, **Then** the summary shows periode, "4 fakturaer", "9 LG04-ordrer" (one
   order per line under the current export mode) and "12 345,67", labeled "eksl./inkl. mva uavklart".
2. **Given** a GODKJENT run, **When** a GODKJENNER exports it, **Then** the written LG04 file is
   read back before the export is completed. Its order count and amount sum are compared with the
   run, and the result ("LG04 stemmer med fakturagrunnlaget": orders and amount, file vs run) is
   stored and shown on the run page.
3. **Given** an export where the LG04 file read back does not match the run (count or amount),
   **When** the export runs, **Then** the export fails. The run stays GODKJENT and no export files
   are kept. The user sees an error with both sets of figures, which says the run is still approved
   and that the forsystem team must be contacted. The failed attempt is logged. Once the defect is
   fixed, the same run can be exported again with no other step.
4. **Given** a run that was exported before this feature existed, **When** a user opens it,
   **Then** the reconciliation is computed from the archived LG04 file and shown the same way.
5. **Given** an EKSPORTERT run, **When** the user looks at the summary, **Then** the figures for the
   covering email (periode, antall fakturaer, samlet beløp) are shown together in one block that
   can be copied in one action.

---

### User Story 2 - Prove that no usage was silently left out (Priority: P1)

Before approving, the approver opens the run's reconciliation view ("Avstemming bruksdata"). For
each product and usage type, it compares what was imported for the period (rows and summed
quantity or amount) with what is on the invoice lines (lines and summed quantity or amount). Below
the table is a list of every imported usage row that did not become an invoice line, each with the
reason, which is the control finding that stopped it (for example "ingen registrert kunde for
organisasjonsnummer 999 999 999").

**Why this priority**: T06 and T10 need Økonomi to confirm that left-out usage was correctly left
out, and that every expected product is present. Forsystem never drops usage silently, but
without this view nobody can *see* that. It is the software half of the routine Økonomi will try
out during the test period.

**Independent Test**: Import demo usage that includes an orgnr with no customer and a product with
no price. Generate the run. Check that the per-product table shows the difference, and that the
list names exactly those rows with the matching reasons. In a clean run, check that every row
matches and the list is empty.

**Acceptance Scenarios**:

1. **Given** a run where every imported row became a line, **When** the user opens the
   reconciliation view, **Then** every product/type row shows imported = invoiced (count and sum),
   the view states "All bruksdata er med i fakturagrunnlaget", and the not-invoiced list is empty.
2. **Given** a run where usage for orgnr X has no registered customer, **When** the user opens the
   view, **Then** X's rows appear in the not-invoiced list with reason MANGLER_KUNDE, and the
   affected product rows show the difference between imported and invoiced.
3. **Given** a run where a product lacks a price or a customer lacks a kundenummer rule, **When**
   the user opens the view, **Then** the affected rows are listed with reason MANGLER_PRIS or
   MANGLER_KUNDENUMMER respectively.
4. **Given** a product that had usage in the previous period but none in this one, **When** the
   user opens the view, **Then** the product is listed with zero imported and zero invoiced, so its
   absence is visible.
5. **Given** a user with only the LESER role, **When** they open the view, **Then** they can read it
   and nothing on it changes data.

---

### User Story 3 - Sample the fakturagrunnlag in a spreadsheet before approving (Priority: P2)

From a run that is GENERERT, GODKJENT or EKSPORTERT, a user downloads the fakturagrunnlag as CSV or
XLSX. Each row is an invoice line, with the customer's name, orgnr, kundenummer, product, usage
type, description, quantity, unit price and amount. The file name says the run's status, so a draft
is never mistaken for the approved basis.

**Why this priority**: T01 asks Økonomi to sample customers and lines. A spreadsheet makes that
fast. It ranks below US1 and US2 because the run and invoice pages already allow sampling on screen.

**Independent Test**: Download the XLSX from a GENERERT run. Check that it has one row per invoice
line, that the amounts sum to the run total in a spreadsheet, and that no export file was recorded
and the run's status did not change.

**Acceptance Scenarios**:

1. **Given** a GENERERT run, **When** a user downloads the XLSX, **Then** they get a file named with
   the periode and status (for example `fakturagrunnlag-2027-01-GENERERT.xlsx`), with one row per
   invoice line and the customer name included.
2. **Given** the downloaded XLSX, **When** the user sums the amount column in a spreadsheet,
   **Then** the result equals the run total in the summary from US1. Quantity, unit price and
   amount are numeric cells, not text.
3. **Given** any pre-approval download, **When** it completes, **Then** the run's status is
   unchanged, no export file is recorded, and the download is logged (who, which run, which format).
4. **Given** a FORKASTET run, **When** the user looks for the download, **Then** it is not offered.
5. **Given** a customer renamed in the register after the run was generated, **When** the user
   downloads the spreadsheet, **Then** it shows the name as it was at generation.
6. **Given** an EKSPORTERT run, **When** the user compares the pre-approval-style download with the
   CSV/XLSX archived at export, **Then** they have the same columns and the same rows.

---

### Edge Cases

- **Run with zero fakturaer** (every row blocked): the summary shows 0 fakturaer, 0 orders, 0,00.
  The reconciliation view lists every imported row as not invoiced.
- **Zero-amount lines** (NULLBELOP): they count as LG04 orders and as invoiced lines. They are not
  "left out".
- **Pass-through cost rows** (AZURE_KOSTNAD, SMS_KOSTNAD) have an amount and no quantity. The
  reconciliation compares amounts for these types and quantities for volume types.
- **Inactive agreement** (INAKTIV_AVTALE): the lines exist but the run cannot be approved. The view
  counts them as invoiced and points to the blocking finding in the run's findings list.
- **The archived LG04 file is missing or unreadable**, or its checksum differs from the one recorded
  at export: the reconciliation shows "kunne ikke avstemmes" as a danger alert with the reason. It
  never shows a silent blank or a match.
- **Rounding**: the LG04 holds whole øre. The run total in kroner × 100 must equal the file's øre sum
  exactly. Any difference, however small, is a mismatch.
- **Export mode changes** (OQ-1, several lines per order): the "number of LG04 orders" figure
  follows the active mode. The summary never assumes orders = lines.
- **Correction after a discarded run** (BF1 T08): discard the run, re-import the period, and
  generate again. The new run reconciles against the new import, and the discarded run still
  reconciles against its own import.
- **A FORKASTET run** keeps its summary and reconciliation view for history (read-only), but offers
  no spreadsheet download.

## Requirements *(mandatory)*

### Functional Requirements

**Run summary and LG04 reconciliation (C-14)**

- **FR-001**: The run page MUST show a summary for every run with: periode, antall fakturaer,
  number of LG04 orders the export produces under the active export mode, number of invoice lines,
  and total amount.
- **FR-002**: The total amount MUST be labeled "eksl./inkl. mva uavklart" while OQ-11 is open.
  No VAT calculation may be added.
- **FR-003**: The summary figures MUST be derived from the run's stored fakturaer and lines, so they
  are identical every time the page is opened.
- **FR-004**: On export, the system MUST read back the LG04 file it has just produced, exactly as it
  will be delivered. It MUST count the orders and sum the amounts in the file, and compare both with the run.
- **FR-005**: If the count or the amount differs, the export MUST fail as a whole. The run stays
  GODKJENT, and no export files are kept or recorded, so nothing wrong can be downloaded or sent. The
  failure MUST be written to the hendelseslogg with user, time and both sets of figures.
- **FR-005a**: The user MUST see an error on the run page that states:
  - that the export was stopped and no files were produced
  - the file figures and the run figures side by side
  - that the run is still approved and nothing needs to be redone
  - that the cause is an error in forsystem, so the user should contact the forsystem team and
    export again once it is fixed

  Example: *"Eksport stoppet: LG04-filen har 8 ordrer / 12 300,00, fakturagrunnlaget har
  9 ordrer / 12 345,67. Ingen filer er laget, og kjøringen er fortsatt godkjent. Dette er en feil
  i forsystemet — kontakt forsystem-teamet og eksporter på nytt når feilen er rettet."*
- **FR-006**: A successful reconciliation MUST be shown on the run page for every EKSPORTERT run,
  with file figures, run figures and the outcome. For runs exported before this feature, it MUST be
  computed from the archived LG04 file.
- **FR-007**: If the archived LG04 file cannot be read or its checksum differs from the recorded
  one, the run page MUST show this as a danger alert. It must not show a match or hide the section.
- **FR-008**: The run page MUST offer the covering-email figures (periode, antall fakturaer, samlet
  beløp with the VAT label) as one block that can be copied in a single action.

**Usage reconciliation (C-06)**

- **FR-009**: Every run MUST have a read-only reconciliation view. Per product and usage type, it
  shows imported usage (number of rows, summed quantity for volume types, summed amount for cost
  types) and invoiced usage (number of lines, the same sums), with the difference.
- **FR-010**: The view MUST include every active product, even with no usage in the period, so a
  missing product is visible (T10).
- **FR-011**: The view MUST list every imported usage row of the run's import that has no invoice
  line, with orgnr, product, type, quantity or amount, and the reason. The reason is the control
  finding code and its message.
- **FR-012**: When every imported row has an invoice line, the view MUST say so explicitly.
- **FR-013**: The view MUST only cover usage that reached forsystem. It MUST state that usage
  filtered out in the datavarehus is not shown (see proposed open question P-1 in docs/11).

**Pre-approval spreadsheet (C-01)**

- **FR-014**: Users MUST be able to download the run's fakturagrunnlag as CSV and as XLSX from any
  run in status GENERERT, GODKJENT or EKSPORTERT. FORKASTET runs MUST NOT offer it.
- **FR-015**: The spreadsheet MUST contain one row per invoice line with at least: periode, ordre,
  kundenummer, customer name, orgnr, fakturamottaker, product, usage type, description, quantity,
  unit price, amount, fakturareferanse and bestillingsnummer.
- **FR-015a**: The customer name MUST be stored with each faktura when the run is generated, so
  later changes in the customer register do not change a run's spreadsheet, run page or PDF. For
  runs generated before this feature, the current registry name MUST be shown and marked as such.
- **FR-016**: In the XLSX, quantity, unit price and amount MUST be numeric cells.
- **FR-017**: The file name MUST contain the periode and the run status at the time of download.
- **FR-018**: A pre-approval download MUST NOT change the run, MUST NOT be recorded as an export
  file, and MUST be written to the hendelseslogg (user, run, format).
- **FR-019**: The CSV/XLSX archived at export MUST have the same column set as the pre-approval
  download, so the draft and the approved basis can be compared column by column.

**Cross-cutting**

- **FR-020**: All views and downloads in this feature MUST be available to the LESER role.
  None of them changes invoicing data.
- **FR-021**: The billing period MUST always come from the run. It MUST never be derived from the
  current date.
- **FR-022**: The LG04 output itself MUST NOT change. The existing golden-file tests MUST stay
  byte-identical.
- **FR-023**: Re-importing a period's usage after a run for that period was discarded MUST succeed.
  The discarded run's usage MUST be kept and remain reachable from that run. Today this fails with a
  database error (found during planning, research R1). It blocks the BF1 T08 correction flow and
  the reconciliation of discarded runs.

### Key Entities

- **Kjøringssammendrag (run summary)**: derived figures for one run: periode, antall fakturaer,
  antall LG04-ordrer, antall linjer, sum beløp. Computed, not separately maintained.
- **LG04-avstemming (LG04 reconciliation)**: the outcome of comparing an LG04 file with its run. It
  holds the file's order count and øre sum, the run's figures, match or mismatch, when it was
  checked, and the export file it refers to.
- **Bruksdataavstemming (usage reconciliation)**: per run, per product and type: imported count and
  sum against invoiced count and sum. It also lists the usage rows with no invoice line and the
  control finding that explains each one.
- **Fakturagrunnlag-regneark (spreadsheet)**: a download of a run's lines, with columns per FR-015.
  It is identical in content whether produced before approval or archived at export.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Økonomi can do BF1 T15 (count and total in the fakturagrunnlag vs LG04) from the run
  page alone in under 1 minute, without opening the LG04 file or a calculator.
- **SC-002**: In 100 % of exports, an LG04 whose order count or amount differs from the approved run
  is stopped before it can be downloaded.
- **SC-003**: For any run, every imported usage row is accounted for: the invoiced rows plus the
  listed not-invoiced rows equal the imported rows, per product and type, with no remainder.
- **SC-004**: Økonomi can start sampling a generated run in a spreadsheet within 30 seconds of
  generation, before approval.
- **SC-005**: The spreadsheet's amount column sums exactly to the run total shown on the run page,
  to the øre.
- **SC-006**: BF1 test cases T01, T06 (software part), T10 and T15 can be marked as passable with
  existing features plus this one.

## Assumptions

- **A failed reconciliation blocks the export** rather than warning (decided 2026-10-12). The
  file is produced from the same lines as the run, so a mismatch can only mean a defect in
  forsystem. Økonomi cannot judge it away. A warning would leave the run EKSPORTERT, which is final
  by design (it cannot be discarded or exported again). That would leave Økonomi holding a wrong
  file with no way to produce a correct one. Blocking rolls everything back, so after a fix the
  same run can simply be exported again.
- The "number of LG04 orders" follows the export mode that is active when the page is viewed (today
  one order per invoice line, OQ-1). After export, the reconciliation's file figure is
  authoritative.
- **The customer name is frozen at generation** (decided 2026-10-12), like kundenummer and
  fakturamottaker already are. It was left out of the original snapshot set only because it never
  goes to LG04. A control document should show the run as it was generated, though, not a mix of
  frozen and live values. Runs generated before this feature have no frozen name. For those, the
  current registry name is shown, marked as such.
- Usage that the datavarehus filters out before it reaches forsystem (for example, usage under
  inactive agreements) is outside forsystem's control. Økonomi's T06 routine needs that part from the
  datavarehus team (proposed open question P-1 in docs/11).
- The reconciliation view compares against the usage import the run was generated from. Usage
  imports are blocked while a non-discarded run exists for the period, so that import is the
  period's current one.
- "Antall fakturaer" in the covering email means forsystem fakturaer (grouped by kundenummer). How
  many invoices Unit4 creates from the orders is verified in BF1 T05 (OQ-1).
- No new role is introduced. Reading is LESER, and export stays GODKJENNER.
- Out of scope: VAT calculation (OQ-11), usage filtered out in the datavarehus (P-1), comparing two
  runs (C-07), and sending the email itself.
