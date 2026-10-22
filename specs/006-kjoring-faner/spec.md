# Feature Specification: Run page in tabs, with progress and invoice search

**Feature Branch**: `006-kjoring-faner`

**Created**: 2026-10-22

**Status**: Implemented (2026-10-22)

**Input**: User description: "Continue with the full run page redesign after the quick wins (005): tabs
(Oversikt, Kontroller, Fakturaer, Filer, Historikk), a progress strip, invoice search and paging, and
a richer run list."

## Context

Spec 005 put the next step first and grouped the findings. That fixed the top of the page, but
everything still sits on one long page, and the page grows with the run:

- A production run has about 300 invoices. They fill most of the page, and none of them can be
  searched or sorted.
- The usage reconciliation and the LG04 re-check are separate pages, linked from the Filer list. So
  the evidence a reviewer must read before approving is split between the run page and a side trip.
- The status shows where a run is right now. It doesn't show how far the run has come, or who did
  each step and when, although the audit log records all of it.
- The run list shows only the period, status and names. To see a run's total or whether it has
  problems, you have to open each run.

The users are the FORVALTER (generates, corrects data), Økonomi's approver (GODKJENNER), and
reviewers or auditors (LESER) who come back to a run months later. Approvers read evidence and
decide. Auditors look things up.

Out of scope:
- a comparison with the previous month (it needs a business definition of what to compare)
- the four-eyes rule (`feat/sidemannskontroll`, not merged)
- any change to how runs are generated, approved or exported

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One run, five focused views (Priority: P1)

A run has five tabs under its title, and each tab has its own address:

- **Oversikt**: the progress strip, the next-step panel and the summary.
- **Kontroller**: the grouped findings, the usage reconciliation, and the LG04 reconciliation once
  the run is exported.
- **Fakturaer**: the invoices, searchable and paged.
- **Filer**: downloads and archived files.
- **Historikk**: the audit trail for this run.

The tab bar shows counts where they help. Kontroller shows the number of findings, coloured by the
worst severity. Fakturaer shows the number of invoices.

**Why this priority**: This is what makes the page navigable. Each job (decide, check, look up,
download, audit) gets its own view, and nothing grows unbounded on the first screen.

**Independent Test**:

1. Open a run and click each tab. The URL changes, the browser Back button returns to the previous
   tab, and a copied link opens the same tab.
2. The header (breadcrumbs, title, status, tabs) is identical on every tab, and the current tab is
   marked for screen readers.

**Acceptance Scenarios**:

1. **Given** a run, **When** the user opens it, **Then** the Oversikt tab is shown and marked as
   current.
2. **Given** any tab, **When** the user follows a link to it, **Then** that tab opens directly,
   without first showing Oversikt.
3. **Given** a run with 2 blocking findings, **When** any tab is shown, **Then** the Kontroller tab
   carries a red "2" badge. With only warnings, the badge is a warning colour with the warning
   count. With no findings, there is no badge.
4. **Given** the old address of the usage reconciliation page, **When** it is opened, **Then** the
   user lands on the Kontroller tab at the usage section.
5. **Given** an invoice detail page, **When** it is shown, **Then** its breadcrumbs lead back to the
   Fakturaer tab of its run.

---

### User Story 2 - See how far the run has come (Priority: P1)

The Oversikt tab starts with a progress strip of four steps, each with who did it and when:

1. **Generert**
2. **Kontrollert og godkjent**
3. **Eksportert**
4. **Sendt til regnskap**

Done steps are marked as done. The current step is highlighted, and later steps are muted. The last
step is a manual e-mail, so it is never shown as done: once exported, it is highlighted as the
current step. A discarded run shows that it was discarded, by whom and when.

**Why this priority**: The status tag says where the run is. The strip says how far it has come
and who signed off, which approvers and auditors ask about first.

**Independent Test**: For a run that has been exported, the strip shows "Generert" with the
generating user, "Godkjent" with the approver, and "Eksportert" with the exporting user, each with a
date. "Send til regnskap" is shown as the current step.

**Acceptance Scenarios**:

1. **Given** a GENERERT run, **When** Oversikt is shown, **Then** step 1 is done, with user and time
   from the run, and step 2 is current.
2. **Given** an EKSPORTERT run, **When** Oversikt is shown, **Then** steps 1–3 are done. The export
   user and time come from the audit trail. Step 4 is current.
3. **Given** a FORKASTET run, **When** Oversikt is shown, **Then** the strip shows that the run was
   discarded, by whom and when (from the audit trail), instead of the remaining steps.
4. **Given** an older run whose export has no audit entry, **When** Oversikt is shown, **Then**
   step 3 is shown as done without a name and time.

---

### User Story 3 - Find an invoice quickly (Priority: P1)

The Fakturaer tab lists the run's invoices 50 at a time. The user can:

- search by customer name, organisation number, kundenummer or order number
- sort by order number, customer or amount

The result count and the total of the matching invoices are shown above the table.

**Why this priority**: Questions from Økonomi and tjenesteeiere are about one customer's invoice.
With 300 invoices, finding it by scrolling is slow and error-prone.

**Independent Test**: In a run with 300 invoices, search for a customer's organisation number. A
single row appears, with the count "1 faktura" and its amount as the total. Clearing the search
shows page 1 of 6.

**Acceptance Scenarios**:

1. **Given** 120 invoices, **When** the tab opens, **Then** invoices 1–50 are shown, sorted by
   order number, with page navigation to pages 2 and 3.
2. **Given** a search term, **When** it is submitted, **Then** only invoices whose customer name,
   orgnr, kundenummer or order number contains the term (ignoring case) are listed. The count and
   the total reflect the matches.
3. **Given** the user sorts by amount, **When** the list is shown, **Then** it is ordered by amount,
   largest first, and the column header shows the sort direction.
4. **Given** a search and a sort, **When** the user changes page, **Then** both are kept, and the
   address reflects them so it can be shared.
5. **Given** a search with no matches, **When** the list is shown, **Then** it says nothing matched
   and offers to clear the search.

---

### User Story 4 - Know which runs need attention from the list (Priority: P2)

The run list shows, for each run:

- the month
- a plain-language status: Venter på godkjenning, Klar til eksport, Eksportert or Forkastet
- the number of invoices
- the total amount
- the number of blocking findings and warnings
- who generated and approved it

**Why this priority**: The list is where people start each month and come back for audits.
Seeing the total and problems there saves opening each run. It matters less than the run page
itself.

**Independent Test**: With one approved run and one generated run with blocking findings, the list
shows both totals, and a red blocking count on the generated run, without opening either.

**Acceptance Scenarios**:

1. **Given** runs in each status, **When** the list is shown, **Then** each shows its plain-language
   status, its invoice count and its total.
2. **Given** a run with findings, **When** the list is shown, **Then** its blocking and warning
   counts are visible, blocking in red.

---

### User Story 5 - The run's own history (Priority: P3)

The Historikk tab lists every audit event recorded for the run, newest first: generated, approved,
discarded, export started, export stopped, downloaded. Each shows who, when and the recorded
details.

**Why this priority**: Auditors ask who did what to a run. Today they search the global event log.
The data exists, so this tab is cheap.

**Independent Test**: For a run that was generated, approved and exported, then had a file
downloaded, the tab lists those four events with users and times.

**Acceptance Scenarios**:

1. **Given** a run with audit events, **When** Historikk is shown, **Then** all of the run's events
   are listed newest first, and only that run's events.

### Edge Cases

- **No invoices.** A run with 0 invoices shows "Ingen fakturaer i denne kjøringen" on the Fakturaer
  tab and no page navigation.
- **Bad paging input.** A page number beyond the last page shows the last page. A page number below
  1 shows page 1. Unknown sort keys fall back to order number.
- **Long search terms** are trimmed. Searching never errors.
- **Actions from a tab.** Approve, export and discard live on Oversikt only. After any of them, the
  user returns to Oversikt, as today.
- **Stopped export.** The stopped-export explanation from spec 002 shows on Oversikt.
- **Readers.** All tabs are readable by LESER. Actions still need their roles, as today.
- **Print.** Every tab prints without the header navigation, tabs and buttons.
- **Phone width.** On a phone the tab bar scrolls sideways within itself, and the page never does.

## Requirements *(mandatory)*

### Functional Requirements

**Tabs**

- **FR-001**: A run MUST have five tabs: Oversikt, Kontroller, Fakturaer, Filer and Historikk. Each
  tab has its own address, and Oversikt is the run's default address.
- **FR-002**: Every tab MUST share a header: breadcrumbs, the title with the month, the
  plain-language status, and the tab bar. The current tab is marked visibly and for assistive
  technology.
- **FR-003**: The Kontroller tab MUST show a count badge:
  - with blocking findings: red, counting the blocking findings
  - with only warnings: warning colour, counting the warnings
  - with no findings: no badge

  The Fakturaer tab MUST show the invoice count.
- **FR-004**: The usage reconciliation's old address MUST lead to the Kontroller tab's usage
  section. The LG04 re-check and the invoice detail stay where they are, with breadcrumbs through
  their tab.

**Overview**

- **FR-005**: Oversikt MUST show, in order:
  1. the stopped-export explanation (when present)
  2. the progress strip
  3. the next-step panel (spec 005)
  4. the summary

  The panel's checklist links go to the Kontroller tab.
- **FR-006**: The progress strip MUST show four steps with who and when:
  - **Generert**: from the run.
  - **Kontrollert og godkjent**: from the run.
  - **Eksportert**: from the audit trail. Shown as done without who and when if no audit entry
    exists.
  - **Sendt til regnskap**: never done; current once exported.

  A FORKASTET run MUST show the discard (who and when, from the audit trail) instead of the steps
  after it.

**Checks**

- **FR-007**: Kontroller MUST show:
  - the grouped findings (spec 005)
  - the full usage reconciliation, the content of today's reconciliation page
  - the LG04 reconciliation result and the re-check link, when exported

**Invoices**

- **FR-008**: Fakturaer MUST list invoices 50 per page:
  - search by customer name, orgnr, kundenummer or order number (contains, case-insensitive)
  - sort by order number (default, ascending), customer (A–Z) or amount (largest first)
  - page navigation
  - the match count and the total amount of the matches
- **FR-009**: The search, sort and page MUST be part of the address. Bad values fall back to their
  defaults, and a page out of range is clamped to the nearest valid page.

**Files and history**

- **FR-010**: Filer MUST hold the downloads and archived files from spec 005's Filer section.
- **FR-011**: Historikk MUST list the run's audit events, newest first, with time, user, action and
  details.

**Run list**

- **FR-012**: The run list MUST show, per run:
  - month
  - plain-language status
  - invoice count
  - total amount
  - blocking and warning counts
  - generated by
  - approved by

**Unchanged**

- **FR-013**: Actions MUST stay on Oversikt with their confirmation dialogs. Business rules, roles,
  downloads and the LG04 output MUST NOT change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a run with 300 invoices, a user finds one customer's invoice in under 15 seconds
  using search.
- **SC-002**: No run page is longer than about two screens (1280×800) by default, whatever the
  number of invoices or findings.
- **SC-003**: From the run list alone, a user can tell which runs have blocking findings and what
  each run totals, without opening any run.
- **SC-004**: Every view of a run can be shared and reopened by its address, including the search
  and page on Fakturaer.
- **SC-005**: For any exported run, who generated, approved and exported it, and when, can be read
  from Oversikt without opening the global event log.

## Assumptions

- **Page size.** 50 invoices per page fits two screens and keeps the count of pages low (about 6
  for 300 invoices). There is no setting to change it.
- **Search and sort run in memory.** They work over the run's invoice list, which is a few hundred
  rows. No database index or query change is needed.
- **Plain-language statuses** are display labels only. Stored statuses and logic are unchanged.
- **Mapping:**
  - GENERERT → Venter på godkjenning
  - GODKJENT → Klar til eksport
  - EKSPORTERT → Eksportert
  - FORKASTET → Forkastet
- **Run list counts** are computed per run when the list is shown. There are about 12 runs a year,
  so this is cheap.
- **Who exported and when** come from the audit trail's EKSPORTERTE event, already recorded by the
  export.
