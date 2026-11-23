# Feature Specification: Import the datavarehus export with invoice flags

**Feature Branch**: `017-dwh-flagg-import`

**Created**: 2026-11-13

**Status**: Draft — ready for clarification/planning

**Input**: New datavarehus export `Mv_altinn_usage_last_3m_flag_20261106v2` (30 MB, 113 357 daily rows,
2026-08-01 … 2026-10-31). It adds the columns `source_table`, `resource_id`, `flag_inv_altinn`,
`flag_inv_dpv` and `regel`. Goal: this kind of file can be uploaded and processed correctly.

## Context

The file is the DWH's own pre-filtered view of what is billable. Profiling it against the current
importer (`DwhCsv`, `DwhAggregering`; docs/csv-format.md «The datavarehus's own export») shows:

| Finding | Count |
|---|---|
| Rows | 113 357 (Aug 20 570, Sep 39 257, Oct 53 530); 256 distinct orgnr |
| `flag_inv_altinn = 1` | 60 096 — all have a valid 9-digit orgnr |
| `flag_inv_altinn = 0` | 53 261 — zero-volume rows (40 420 have quantity 0), rows excluded for inactive CRM agreement (`ekskludert=ikke_aktiv_crm`, positive quantities too), and DPV rows |
| `flag_inv_dpv = 1` | 8 897 — resources `eformidling-dpv-administrasjon` / `-taushetsbelagt`, with and without the `urn:altinn:resource:` prefix; never together with `flag_inv_altinn = 1`; 1 315 have no orgnr |
| `organisation_number` not 9 digits | 919 empty, plus the literal `NULL` (523), `unknown` (86), `owner`, or an organisation *name* (`digitaliseringsdirektoratet`, 137) |
| `quantity` NULL on non-appinfra rows | 2 310 (zero-count days) |
| Several rows per (date, org, product, source_table, resource_id) | 2 298 groups |
| `bestillingsnummer` | NULL on every row |
| `fakturareferanse` / `kundenummer` NULL | 8 946 / 8 943; 3 flagged rows have no fakturareferanse |
| Products | Autorisasjon, Altinn studio, Melding, Varsling e-post, Varsling SMS (Norge), Varsling SMS (utland), Formidling, `altinn-studio-applikasjonsinfrastruktur` (amount only) |
| Autorisasjon | only Sep–Oct (history varies per product, OQ-4) |

Consequences with today's importer: it aborts on the first row whose orgnr is missing or not digits;
rejects rows with NULL quantity; sums excluded and DPV rows into the basis (wrong customers, inflated
volumes — e.g. 2.4 M Autorisasjon in September that the DWH marks «neither»); and rejects every row outside
the one declared period, which for a three-month file means two thirds of it.

`regel` is a `; `-separated explanation (`orgnr=<how resolved>; navn=<how resolved>;
inkludert|ekskludert|ekskludert_altinn=<reason>`). Its vocabulary is not a contract (OQ-25).

## Clarifications

### Session 2026-11-13 (defaults settled here; the underlying facts are open questions)

- Q: Which rows go into the invoice basis? → A: Only rows with `flag_inv_altinn = 1`. The flag is the
  DWH's decision (it checks usage against active agreements, OQ-4); forsystem's own controls stay the
  second net.
- Q: DPV rows (`flag_inv_dpv = 1`)? → A: Not billed by forsystem and never summed into the basis.
  Whether they are billed elsewhere, and whether forsystem should show them, is OQ-26. They are counted
  and reported, not silently dropped.
- Q: Rows with flag 0 and positive volume (excluded for inactive CRM agreement)? → A: Not billed;
  reported per reason and organisation so Økonomi can see usage that earns no invoice.
- Q: Multi-month file? → A: The user declares the period as today; only that month's rows are used, other
  months are reported as «utenfor perioden» (a count per month), not as errors.
- Q: Flag semantics for backward compatibility? → A: A file *without* `flag_inv_altinn` (the 2026-08/09
  exports) behaves exactly as today.
- Q: Is a row's `fakturareferanse`/`kundenummer` trusted? → A: As today: cross-check only; the registry is
  the master (specs/001, 012).

### Session 2026-11-13 (clarify)

- Q: How much source detail is stored per imported month for traceability? → A: A grouped summary: one row
  per (orgnr, product, source_table, normalised resource_id) with day-row count, summed quantity/amount and
  the distinct `regel` texts. Individual day rows are not stored in the database; the archived file
  (with checksum) remains the row-level proof.
- Q: Multi-month file handling? → A: One declared period per upload, as today; other months are reported
  as outside the period. Importing several months in one upload stays out of scope.
- Q: Which rows may create missing customers? → A: Only used rows (flag 1, declared period). Excluded and
  DPV rows never create customers, since their orgnr/name are placeholders.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upload the flagged export and get a correct basis (Priority: P1)

Økonomi downloads the 3-month export from the datavarehus, opens *Bruksdata → Last opp fil*, declares
period 2026-10 and uploads it. Only rows the DWH flagged for Altinn invoicing in October become usage;
the staging report shows how many rows were used and how many were left out, and why.

**Why this priority**: Without it the real DWH file cannot be imported at all; with a naïve fix it would
be imported wrongly and reach an invoice.

**Independent Test**: Upload a reduced fixture cut from the real file for 2026-10; the resulting usage per
(orgnr, product) equals the sum of `quantity` over rows with `flag_inv_altinn = 1` in October, and
`amount_nok` for appinfra.

**Acceptance Scenarios**:

1. **Given** the fixture, **When** period 2026-10 is imported, **Then** usage contains only
   `flag_inv_altinn = 1` rows dated in October, summed per orgnr and product, and the file is accepted.
2. **Given** rows with orgnr `NULL`, empty, `unknown`, `owner` or a name, all with flag 0, **When**
   imported, **Then** none of them causes a rejection of the file.
3. **Given** a flagged row (flag 1) with a malformed orgnr, **When** imported, **Then** it is rejected
   with its line number — a flagged row without a billable customer is a real error.
4. **Given** flag-1 rows with `quantity` 0 or NULL on a volume product, **When** imported, **Then** zero
   rows are ignored without error; NULL on a volume product is treated as zero.
5. **Given** an older file without the new columns, **When** imported, **Then** the result is unchanged
   from today (regression).

---

### User Story 2 - See what was left out and why (Priority: P2)

Before accepting the import, the user sees a breakdown: rows used; rows outside the period (per month);
DPV rows; rows excluded for inactive agreement or no positive volume (counts and volume sums per product).

**Why this priority**: The DWH excluded 3 055 rows with `ikke_aktiv_crm`, some with large volumes; Økonomi
must be able to tell «not billed on purpose» from «lost».

**Independent Test**: Counts in the report equal counts computed independently from the fixture.

**Acceptance Scenarios**:

1. **Given** the fixture, **When** the preview is shown, **Then** it lists used / outside period / DPV /
   excluded (by `regel` reason) with row counts and volume per product.
2. **Given** excluded rows with positive volume, **When** shown, **Then** the organisations (or «ukjent
   orgnr») with most excluded volume are listed.

---

### User Story 3 - Trace a usage figure back to the DWH rows (Priority: P2)

For an imported usage row the user can see which source tables and resources it came from, and the
`regel` explanation of the contributing rows (constitution III).

**Why this priority**: Customers ask «why this volume»; today only the archived raw file answers that.

**Independent Test**: For one (orgnr, product), the drill-down lists the contributing DWH rows grouped by
`source_table` and `resource_id`, summing to the usage figure.

**Acceptance Scenarios**:

1. **Given** an imported file, **When** the user opens the traceability view for a usage row, **Then**
   source table, resource id (prefix `urn:altinn:resource:` normalised) and the number of day rows are shown.
2. **Given** the file is archived with its checksum, **When** a run is explained later, **Then** the same
   breakdown can be reproduced from the archive.

---

### User Story 4 - A plausible but unknown input fails loudly (Priority: P3)

If a flag column holds a value other than `0`/`1`, or a product name is not mapped, the user gets a clear
message instead of silent inclusion/exclusion.

**Acceptance Scenarios**:

1. **Given** `flag_inv_altinn = 2` or empty on some row, **When** imported, **Then** that row is rejected
   with line number and the value.
2. **Given** a new `product_name`, **When** imported, **Then** the existing «legg inn kobling under
   Produkter → Kildenavn» message appears (OQ-18), once per name.

### Edge Cases

- Flag-1 row dated outside the declared period → reported as outside period, never an error.
- Same (date, org, product, source_table, resource_id) repeated: rows are summed (2 298 groups exist);
  this is not a duplicate error. Whether they are true duplicates is OQ-27.
- `resource_id` appears both with and without `urn:altinn:resource:`; both are the same resource.
- Appinfra rows carry `amount_nok` and NULL `quantity`; volume products carry the reverse.
- Flagged rows without `fakturareferanse` (3 in the file): imported, then caught by the existing
  missing-reference control (specs/012), not rejected at import.
- A file where nothing is flagged for the declared period: import is refused with an explanation, not an
  empty successful import.
- Files of ~30 MB must upload and process without the user waiting more than a short time.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept the new export, recognising it by its header (as in specs/001 «format
  recognised») and tolerating the extra columns `source_table`, `resource_id`, `flag_inv_altinn`,
  `flag_inv_dpv`, `regel`.
- **FR-002**: When `flag_inv_altinn` is present, the system MUST build usage only from rows where it is `1`
  and MUST NOT use rows where it is `0`.
- **FR-003**: Rows with `flag_inv_dpv = 1` MUST NOT become usage in this feature, and MUST be counted and
  reported (OQ-26).
- **FR-004**: Rows not used for any reason MUST NOT cause the file to be rejected, including missing,
  `NULL`, non-numeric or name-valued `organisation_number` and NULL `quantity`.
- **FR-005**: A used (flag 1) row with an invalid orgnr, a negative or non-numeric quantity/amount, or a
  flag value other than `0`/`1` MUST be rejected with line number and reason, under the existing
  all-or-nothing rule.
- **FR-006**: Only rows dated in the declared period are used; rows in other months MUST be reported per
  month and MUST NOT be errors. The period stays an explicit parameter (constitution IV).
- **FR-007**: Before confirmation the system MUST show used rows, rows outside the period, DPV rows and
  excluded rows (grouped by the reason in `regel`), with row counts and volume per product.
- **FR-008**: The system MUST store, per imported month, a grouped summary per (orgnr, product,
  source_table, normalised resource_id) holding day-row count, summed quantity/amount and the distinct
  `regel` texts, so a usage figure can be traced to its sources; the groups MUST sum to the usage figure.
  The original file MUST be archived with checksum.
- **FR-009**: `resource_id` MUST be normalised by removing the `urn:altinn:resource:` prefix before grouping
  or display.
- **FR-010**: Product-name mapping, volume vs. amount handling, øre rounding of amounts and the customer-
  creation-from-file option MUST work as today for the flagged rows, and customers MUST be offered for
  creation only from used rows (never from excluded or DPV rows).
- **FR-011**: Files without the flag columns MUST import exactly as before.
- **FR-012**: A period with no flagged rows MUST be refused with an explanation.
- **FR-013**: The import MUST NOT guess meaning of unknown `regel` tokens; unknown tokens are shown as-is in
  the report and never change inclusion (inclusion follows the flags only).
- **FR-014**: docs/csv-format.md, docs/07-open-questions.md (OQ-3, OQ-4, OQ-18 updated; OQ-25–27 added) and
  the user guide MUST describe the new behaviour.
- **FR-015**: The import MUST be recorded in the event log as today, including counts of used and left-out
  rows.

### Key Entities

- **DWH day row**: One day's usage for an organisation and product in a source table, with the DWH's two
  invoice flags and its `regel` explanation. Transient, plus archived raw file.
- **Import report (forhåndsvisning)**: The breakdown of used / outside period / DPV / excluded rows.
- **Usage source trace**: For each monthly usage row, the contributing (source table, resource) groups and
  day-row counts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The real 113 357-row file imports for each of 2026-08, 09 and 10 without manual editing, and
  per-product totals equal an independent sum of `flag_inv_altinn = 1` rows for that month (zero difference).
- **SC-002**: No flag-0 or DPV row contributes to any usage figure (verified on the fixture: 0 rows).
- **SC-003**: Every left-out row is accounted for: used + outside period + DPV + excluded = total rows.
- **SC-004**: A 30 MB file is uploaded and its report shown in under 30 seconds.
- **SC-005**: Existing import tests and golden files still pass unchanged (no regression for older exports).
- **SC-006**: For any imported usage row, its source tables and resources can be listed in two clicks.

## Assumptions

- `flag_inv_altinn` is the DWH's final answer on «bill on the Altinn invoice»; forsystem does not
  recompute it from `regel` (OQ-25 asks the DWH to confirm and to make `regel` a stable contract).
- The upload keeps declaring a single period; importing several months in one go is out of scope.
- DPV billing (and any DPV invoice basis) is out of scope until OQ-26 is answered.
- Test fixture is a reduced, anonymised cut of the real file (a few hundred rows covering every flag/regel
  combination, every product, NULL/`unknown`/name orgnr, urn-prefixed resources, repeated day rows); the
  30 MB file itself is not committed.
- Prices, units and SMS products are unchanged (OQ-3, OQ-18, OQ-21).

## Open questions raised (to be added to docs/07)

- **OQ-25**: Contract for `flag_inv_altinn`, `flag_inv_dpv` and `regel` — are flags final, is `regel`'s
  vocabulary stable, will the 3-month «last_3m_flag» file become the permanent export or the view/API?
  Owner: DWH/platform.
- **OQ-26**: What happens to DPV rows (`eformidling-dpv-*`, 8 897 rows, 1 315 without orgnr)? Billed by
  another process, by forsystem later, or free? Owner: Økonomi + product owners.
- **OQ-27**: Are repeated rows for the same (date, org, product, source_table, resource_id) (2 298 groups)
  legitimate separate events or duplicates in the view? Owner: DWH.
