# Feature Specification: Usage import from the datavarehus API

**Feature Branch**: `feat/dwh-usage-api`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Bruksdata is uploaded manually as CSV today, but the datavarehus exposes
an API (`mv_altinn_usage_monthly`) that can give us that data. Find out the model behind it, what we
need to implement it, and automate it if sensible."

## Context

Today a FORVALTER exports usage from somewhere, shapes it into the CSV format (`docs/csv-format.md`)
and uploads it per period. The datavarehus (DWH) now publishes the billable usage basis through a
read API. This feature lets forsystem fetch a period's usage straight from the DWH, run it through
the same validation and preview it already uses for CSV, and lets an operator confirm it. A
scheduled fetch then prepares the import automatically once the month's data is complete, so the
operator only reviews and confirms. CSV upload stays as the fallback.

Findings from probing the API on 2026-10-06 that shape the requirements are recorded in
[research.md](./research.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fetch a period's usage from the DWH (Priority: P1)

A FORVALTER opens *Bruksdata*, chooses a period, and clicks "Hent fra datavarehus" instead of
uploading a file. forsystem fetches that month's usage, aggregates it to one row per organisation,
product and type, maps the DWH's product names to forsystem products, and shows the familiar
preview: valid rows, rejected rows with reasons, per-product sums and blockers. The FORVALTER
confirms, and the import is stored with source "DWH".

**Why this priority**: It removes the manual export/reshape/upload step and the transcription
errors it invites, using the pipeline that already exists. It is valuable even with no scheduling.

**Independent Test**: With the DWH replaced by a stub serving a fixture month, fetch the period,
see the preview, confirm, and verify the stored rows equal the fixture's monthly sums.

**Acceptance Scenarios**:

1. **Given** the DWH holds daily usage for September 2026, **When** a FORVALTER fetches period
   2026-09, **Then** the preview shows one row per (organisation, product, type) whose quantity is
   the sum of that month's daily rows.
2. **Given** a DWH product name with no mapping to a forsystem product, **When** the period is
   fetched, **Then** the preview rejects every row for that name with "ukjent produktnavn i
   datavarehus: <name>" and the import cannot be confirmed.
3. **Given** a clean preview, **When** the FORVALTER confirms, **Then** the import is stored with
   source DWH, the period's previous import is replaced (as for CSV), and an audit event is logged.
4. **Given** a non-discarded invoicing run already exists for the period, **When** the period is
   fetched, **Then** the preview shows the same blocker as a CSV upload and cannot be confirmed.

---

### User Story 2 - Trust that the fetch is complete and explainable (Priority: P1)

The FORVALTER and later an auditor must be able to rely on the fetched data being the whole month
and to see exactly what the DWH delivered.

**Why this priority**: The API currently drops rows when paged (see research R2). Silent
incompleteness under-invoices customers; this is a correctness gate, not a nicety.

**Independent Test**: Serve a stub response that signals more pages, or contains rows outside the
period, and verify the fetch is refused with a clear message; serve a clean response and verify the
raw payload is archived with a checksum and linked from the import.

**Acceptance Scenarios**:

1. **Given** the DWH signals that more data exists beyond what was returned, **When** a period is
   fetched, **Then** the fetch fails with "ufullstendig svar fra datavarehus" and nothing is staged.
2. **Given** the DWH returns rows dated outside the requested month, **When** a period is fetched,
   **Then** those rows are rejected with "feil periode".
3. **Given** the DWH returns no rows for the month, **When** a period is fetched, **Then** the
   operator sees "ingen bruksdata i datavarehus for <periode> ennå" and nothing is staged.
4. **Given** a confirmed DWH import, **When** anyone views it, **Then** they can download the exact
   raw response it was built from and see its checksum and fetch time.

---

### User Story 3 - Maintain the DWH product-name mapping (Priority: P2)

A FORVALTER maintains which DWH product name (e.g. "Varsling e-post") maps to which forsystem
product and usage type, without a code change.

**Why this priority**: The DWH's names are display names that differ from forsystem codes and will
grow (SMS, Melding, app infrastructure). Who decides the mapping is a business question; the system
must make it data, not code.

**Independent Test**: Add a mapping, fetch a period containing that name, and see its rows accepted.

**Acceptance Scenarios**:

1. **Given** a mapping "Formidling" → product `formidling`, type BRUKSVOLUM, **When** a period is
   fetched, **Then** "Formidling" rows land on `formidling`.
2. **Given** two DWH names mapped to the same product and type, **When** a period is fetched,
   **Then** their quantities are summed into one row per organisation.
3. **Given** a mapping change, **When** it is saved, **Then** it is audit-logged; existing imports
   are unaffected.

---

### User Story 4 - Prepared automatically after month end (Priority: P3)

Once the month's basis is complete in the DWH, forsystem fetches it on its own, stages the preview
and tells the FORVALTER it is waiting. The FORVALTER reviews and confirms.

**Why this priority**: Convenience on top of US1; worthwhile once US1 has run cleanly for a few
months. It never commits on its own.

**Independent Test**: Trigger the scheduled job for a given period against the stub and verify a
staged import appears for review and that no usage rows are committed.

**Acceptance Scenarios**:

1. **Given** the configured day after month end has arrived, **When** the scheduled fetch runs,
   **Then** a staged (not confirmed) import for the previous month exists and the FORVALTER is
   notified.
2. **Given** a confirmed import already exists for that period, **When** the scheduled fetch runs,
   **Then** it does nothing.
3. **Given** the DWH is unreachable, **When** the scheduled fetch runs, **Then** it records the
   failure, retries on the next schedule, and leaves existing data untouched.

### Edge Cases

- The DWH's response is valid but materially smaller or larger than the previous month (e.g. a
  product missing entirely) → shown as a warning in the preview, not a reject.
- `amount_nok` is present on a row → only accepted if its mapping is a cost type; otherwise rejected.
- A row has a non-integer or negative quantity → rejected.
- Organisation number not 9 digits (foreign entities, OQ-14) → rejected, as for CSV.
- The DWH's `kundenummer`/`fakturareferanse` differ from forsystem's customer registry → warning in
  the preview; forsystem's registry stays authoritative (no silent overwrite).
- The DWH changes data for an already-confirmed month → only picked up by an explicit re-fetch,
  which follows the existing replacement rule and its run blocker.
- The fetch takes too long or times out → fails cleanly; nothing staged.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: FORVALTER MUST be able to fetch usage for an explicit period from the DWH as an
  alternative to CSV upload.
- **FR-002**: The system MUST aggregate the DWH's daily rows to one row per (period, organisation,
  product, type) before validation.
- **FR-003**: DWH rows MUST pass the same validation, blockers, preview and all-or-nothing commit as
  CSV rows, and be stored with source DWH.
- **FR-004**: DWH product names MUST be mapped to forsystem products and usage types through a
  maintained mapping; unmapped names MUST be rejected, never guessed.
- **FR-005**: The system MUST refuse a fetch that is or may be incomplete (more data signalled,
  transport error, unexpected shape) and stage nothing.
- **FR-006**: The system MUST archive the raw DWH response for every confirmed import, with
  checksum and fetch time, and make it downloadable from the import.
- **FR-007**: The system MUST warn (not reject) when DWH-supplied customer references differ from
  the customer registry, and when a product's monthly total deviates sharply from the previous
  confirmed month.
- **FR-008**: The system MUST support a scheduled fetch of the previous month on a configurable day
  that stages the import for review and notifies the FORVALTER; it MUST NOT commit on its own.
- **FR-009**: Committing a DWH import MUST remain an explicit FORVALTER action and be audit-logged.
- **FR-010**: The DWH integration MUST be switchable off by configuration, leaving CSV upload fully
  functional, and local development and tests MUST run without the real DWH.
- **FR-011**: The DWH connection MUST authenticate as forsystem's own identity when the DWH requires
  it; credentials come from configuration/platform identity only.

### Key Entities

- **DWH usage row**: one day's usage for an organisation and DWH product name (quantity, optional
  amount, DWH customer references). Transient input, kept only in the archived raw payload.
- **Product-name mapping**: DWH product name → forsystem product and usage type; maintained by
  FORVALTER, audit-logged.
- **Usage import** (existing): gains source DWH, a link to the archived raw payload, and a staged
  state for scheduled fetches awaiting confirmation.
- **Usage row** (existing): unchanged; monthly total per organisation, product and type.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A FORVALTER goes from "period chosen" to "import confirmed" in under 2 minutes with no
  file handling.
- **SC-002**: For a fixture month, stored monthly totals equal the sum of the source's daily rows
  exactly, for every organisation and product (zero tolerance).
- **SC-003**: 100% of incomplete or malformed DWH responses in the test suite are refused with
  nothing staged.
- **SC-004**: Every DWH-sourced invoice line can be traced to the archived raw response it came from.
- **SC-005**: The January 2027 usage can be imported from the DWH for the February 2027 run, with
  CSV upload still available as fallback.

## Assumptions

- The DWH remains the authoritative source of billable usage from 2027-01-01 (docs/06 §1, OQ-4);
  it already filters out non-billable usage against active agreements.
- The month's basis is complete on the 6th of the following month (OQ-4); the scheduled fetch
  defaults to the 7th and is configurable.
- forsystem's customer registry (and later CRM) stays the master for kundenummer and
  fakturareferanse; DWH values are used only for cross-checking.
- The initial product-name mapping is entered by a FORVALTER and confirmed by the product owners;
  nothing is seeded that has not been confirmed (OQ-18).
- Volumes are reported in the billing unit; any unit conversion is out of scope.
- SMS and Azure cost rows follow once their source and format are settled (OQ-3, OQ-4); this
  feature supports cost-type mappings but does not assume any.
