# Feature Specification: A run page that says what to do next

**Feature Branch**: `005-kjoring-quick-wins`

**Created**: 2026-10-22

**Status**: Implemented (2026-10-22)

**Input**: User description: "Kjøringer is cluttered and hard to navigate. Quick wins only: period label, next-step
panel at the top, grouped findings, one Filer section. No tabs yet."

## Context

The run page (one fakturakjøring) is where the FORVALTER and the approver at Økonomi decide whether
a month's invoices can go to accounting. Today it stacks about eleven blocks in the order they were
built, not the order they are used, and the problems compound in production, where a run has
around 300 invoices:

- **Approval is at the bottom.** Godkjenn and Forkast sit below the full invoice table.
- **Findings are one box each.** Every finding is its own alert, so 30+ warnings of the same kind
  form a wall that hides the few blocking ones.
- **Files are scattered.** Downloads (fakturagrunnlag, export files) and the LG04 check sit in three
  different places.
- **The period is a date.** It shows as "2027-01-01" rather than the month people talk about.

This feature reorders and groups what is already there. Tabs, invoice search and a progress strip
are a later step. Only the period label also reaches the run list and the run sub-pages, where
the same date appears.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the next step first (Priority: P1)

A user opens a run. Right under the title they see a "Neste steg" panel. It says what the run is
waiting for, lists the checks that matter for that step, and holds the one main action. They no
longer scroll past the invoices to find it.

What the panel shows for each status:

| Status | Panel heading | Checklist | Main action | Secondary |
|---|---|---|---|---|
| GENERERT | Kontroller og godkjenn | usage reconciliation, blocking findings, warnings | Godkjenn (blocked while blocking findings exist, with the reason) | Forkast |
| GODKJENT | Eksporter til regnskap | who approved and when | Eksporter | Forkast |
| EKSPORTERT | Send tallene til regnskap | who exported and when; the e-mail figures | Kopier tallene | — |
| FORKASTET | Kjøringen er forkastet | — | Generer perioden på nytt | — |

**Why this priority**: Approving is the reason the page exists, and today it is the hardest thing to
find.

**Independent Test**: Open a GENERERT run with 300 invoices. Godkjenn and Forkast are visible
without scrolling at a normal laptop window size. Repeat for each status.

**Acceptance Scenarios**:

1. **Given** a GENERERT run with no findings and all usage invoiced, **When** the page opens,
   **Then** the panel shows three green checks and an enabled Godkjenn above the summary.
2. **Given** a GENERERT run with 2 blocking findings, **When** the page opens, **Then** the panel
   shows "2 blokkerende funn" as a failed check linking to the findings, and Godkjenn is disabled
   with the reason next to it.
3. **Given** a GODKJENT run, **When** the page opens, **Then** the panel shows who approved it and
   when, and offers Eksporter.
4. **Given** an EKSPORTERT run, **When** the page opens, **Then** the panel shows the figures for
   the e-mail to accounting with a copy button.
5. **Given** a FORKASTET run, **When** the page opens, **Then** the panel offers to generate the
   same period again. Confirming takes the user to the new run.
6. **Given** any run, **When** an action in the panel is used, **Then** it asks for confirmation
   exactly as today (spec 004, FR-012).

---

### User Story 2 - Read findings as a short list (Priority: P1)

Findings are grouped by code. Each group shows the code, how serious it is, how many there are, and
the first message as an example. Blocking groups come first. The group expands to show every
message.

**Why this priority**: The decision to approve depends on understanding the findings. Thirty
identical warnings must read as one line saying "30".

**Independent Test**: Generate a run with 25 AKTIV_KUNDE_UTEN_BRUK warnings and 2 MANGLER_KUNDE
blocking findings. The findings section shows two rows, MANGLER_KUNDE first. Expanding the warning
group shows all 25 messages.

**Acceptance Scenarios**:

1. **Given** findings of two codes, **When** the page opens, **Then** there is one row per code,
   with severity, count and an example message.
2. **Given** blocking and warning groups, **When** they are listed, **Then** every blocking group
   comes before every warning group, and within a severity the larger group comes first.
3. **Given** a group, **When** the user expands it, **Then** every message in the group is listed.
4. **Given** a run with no findings, **When** the page opens, **Then** the findings section says
   there are none.

---

### User Story 3 - All files in one place (Priority: P2)

A single "Filer" section gathers everything that can be downloaded or checked for the run:

- the fakturagrunnlag as XLSX and CSV
- the export files (LG04 and attachments), once exported
- the link to re-check the archived LG04

**Why this priority**: Users go to the run to fetch files for accounting and audit. One place
saves hunting, but it matters less than approving.

**Independent Test**: On an EKSPORTERT run, every download and the LG04 check are inside one
section. Every download link works as before.

**Acceptance Scenarios**:

1. **Given** a GENERERT run, **When** the Filer section is shown, **Then** it offers the
   fakturagrunnlag as XLSX and CSV.
2. **Given** an EKSPORTERT run, **When** the Filer section is shown, **Then** it also lists the
   export files and the LG04 check link.
3. **Given** a FORKASTET run, **When** the Filer section is shown, **Then** it explains that
   discarded runs can't be downloaded.

---

### User Story 4 - The month, not a date (Priority: P2)

Runs are named by their month: "Kjøring januar 2027". This applies to the run page title, the
breadcrumbs of every run sub-page, and the period column of the run list.

**Why this priority**: It's how people talk about the run, and it removes the false precision of
"2027-01-01". It costs almost nothing.

**Independent Test**: Open the run list, a run, and one invoice in it. All three say
"januar 2027".

**Acceptance Scenarios**:

1. **Given** a run for January 2027, **When** any run page is shown, **Then** its title or
   breadcrumbs say "januar 2027" and not "2027-01-01".

### Edge Cases

- **Old runs.** A run generated before the usage reconciliation existed has no known import. The
  reconciliation check then says it can't be checked, and links to the reconciliation page, rather
  than showing a green check.
- **Broken reconciliation.** If the usage reconciliation does not balance, which is a system
  defect, the check is shown as failed with the existing "kontakt forsystem-teamet" wording.
- **Long groups.** A group with hundreds of messages stays collapsed by default. The page length
  doesn't grow with the number of findings.
- **Stopped export.** The stopped-export error from spec 002 still appears at the top, above the
  panel.
- **Print.** The panel's buttons are hidden in print, but the checklist and the e-mail figures are
  printed.

## Requirements *(mandatory)*

### Functional Requirements

**Next-step panel**

- **FR-001**: The run page MUST show a next-step panel directly below the title and above all other
  content (except a stopped-export error), with content per status as in User Story 1.
- **FR-002**: For a GENERERT run, the panel MUST list three checks:
  - **Usage reconciliation**:
    - all invoiced → passed
    - some explained as not invoiced → passed, with the count
    - unknown → neutral
    - broken → failed
  - **Blocking findings**: passed when there are none, failed with the count otherwise.
  - **Warnings**: passed when there are none, warning with the count otherwise.

  Each check that has details MUST link to them.
- **FR-003**: Godkjenn MUST stay disabled while blocking findings exist, with the reason shown, as
  today.
- **FR-004**: A FORKASTET run's panel MUST offer to generate the same period again, through the
  existing generate action, with the period filled in. The existing rule that only one active run
  may exist per period still applies.
- **FR-005**: The panel's actions MUST be the existing actions with the existing confirmation
  dialogs. No new action is introduced except the regenerate shortcut in FR-004, which reuses the
  existing generate action.

**Findings**

- **FR-006**: Findings MUST be shown grouped by code. Each row shows severity, code, count and an
  example message, and expands to the full list.
- **FR-007**: Groups MUST be ordered blocking first, then by count (descending), then by code.

**Files**

- **FR-008**: Fakturagrunnlag downloads, export files and the archived-LG04 check MUST be in one
  "Filer" section, placed after the findings and before the invoice table.

**Period label**

- **FR-009**: Run titles, run breadcrumbs and the run list MUST show the period as month and year in
  Norwegian ("januar 2027").

**Unchanged**

- **FR-010**: Routes, form fields, downloads, roles and the LG04 output MUST NOT change. All
  existing tests MUST keep passing, apart from assertions that test the old date label.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a run with 300 invoices, the main action is visible without scrolling in a 1280×800
  window, for every status.
- **SC-002**: A run with 30 warnings of one code and 2 blocking findings of another shows 2 finding
  rows by default, instead of 32 alert boxes.
- **SC-003**: Every file and check for a run is reachable from a single section.
- **SC-004**: No run page shows the period as a date.

## Assumptions

- **What "usage reconciled" means.** It reuses the existing reconciliation: every imported usage row
  is either invoiced or explained by a finding. No new rule is introduced.
- **Who and when.** "Exported by" uses the run's existing audit fields, plus the export
  reconciliation's stored user and time where available. No new data is recorded.
- **Not in this feature.** Invoice search, paging and sorting, tabs, a progress strip, a
  previous-month comparison and a richer run list are later work.
- **The e-mail figures** keep their current heading ("Til e-post til regnskap"), content and copy
  button. They move into the panel and are shown for EKSPORTERT runs only. The e-mail goes out after
  export, and showing it at GODKJENT invited sending it too early.
