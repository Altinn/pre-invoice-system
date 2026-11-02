# Feature Specification: Versioned correction runs for changed usage

**Feature Branch**: `codex/007-korrigeringskjoring`

**Created**: 2026-11-02

**Status**: Draft — ready for stakeholder review; production export depends on OQ-6

**Input**: User description: "Fetch or upload the same month again after it has already been sent,
compare the old imported basis with the new one, show only what changed, let the user select the
changes to include, and create V2 (and later versions) containing only those corrections."

## Context

Today an exported run is final. A new import for its period is blocked, and the test-only
*Nullstill periode* function solves repeated testing by permanently deleting the month. It is not a
production correction flow.

The desired flow preserves V1 and every source snapshot. After the DWH changes a closed month, a
FORVALTER fetches or uploads a new complete DWH snapshot. Forsystem compares it with what has
actually been invoiced, hides unchanged rows, and shows additions, removals and changed values. The
FORVALTER selects rows and generates a correction run for the same month. That run is labelled V2
and contains only the signed differences: 100 → 120 becomes +20; 100 → 80 becomes -20; an added row
becomes +new; a removed row becomes -old. Further corrections become V3, V4 and so on.

This feature is an **avregning** mechanism and therefore touches OQ-6. The comparison, selection,
version chain and signed correction basis are specified here. The exact Unit4/LG04 representation
of negative corrections MUST be confirmed by Økonomi + VIS and test-imported in tt02 before that
export path can be enabled in production.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See only what changed (Priority: P1)

A FORVALTER opens Bruksdata after January V1 has been exported and fetches January again from the
DWH, or uploads a complete export of the same DWH view. Forsystem archives and validates the new
snapshot, compares it with the effective invoiced basis for January, and presents only changed
usage keys. Each row shows organisation, product, usage type, old value, new value and difference,
marked as added, changed or removed. Unchanged rows are counted in the summary but hidden.

**Why this priority**: Without a trustworthy diff, the user cannot know whether a correction is
needed or avoid resending the 80% that was already correct.

**Independent Test**: Start with an exported V1 containing four usage keys. Submit a valid complete
snapshot in which one is unchanged, one increases, one decreases and one is replaced by a new key.
The page shows four changes (increase, decrease, removal and addition), hides the unchanged key, and
the displayed differences exactly reconcile the two bases.

**Acceptance Scenarios**:

1. **Given** an exported V1 and a complete new snapshot for the same period, **When** the snapshot
   is compared, **Then** only added, removed or value-changed keys are listed and unchanged keys are
   not selectable.
2. **Given** volume changes from 100 to 120, **When** the comparison is shown, **Then** old=100,
   new=120 and difference=+20 are shown on one row.
3. **Given** cost changes from 1 000.00 to 750.00, **When** the comparison is shown, **Then**
   old=1 000.00, new=750.00 and difference=-250.00 are shown without losing decimal precision.
4. **Given** a key exists only in the new snapshot, **Then** it is shown as "Lagt til" with
   difference equal to the new value.
5. **Given** a key exists only in the invoiced basis, **Then** it is shown as "Fjernet" with
   difference equal to the negative old value.
6. **Given** an identical new snapshot, **Then** the page says that nothing changed and offers no
   correction action.
7. **Given** a malformed, partial or possibly incomplete DWH response, **Then** no comparison is
   created and the invoiced basis is untouched.

---

### User Story 2 - Select changes and generate V2 (Priority: P1)

On the comparison page, the FORVALTER checks the changes to include. Nothing is selected by
default; "Velg alle" is available. The FORVALTER can review the selected row count and net amount,
add a required correction reason, and generate a V2 draft. V2 goes through the existing controls,
approval and export lifecycle, but contains invoices and lines only for the selected differences.

**Why this priority**: Selection and delta generation are the requested business outcome. They
prevent already-correct usage from being billed again.

**Independent Test**: Select an increase and a removal from a four-row comparison. Generate V2 and
verify that it has exactly two source changes, one positive and one negative correction line, while
the two unselected changes do not occur in any V2 invoice or export.

**Acceptance Scenarios**:

1. **Given** a comparison with changes, **When** no checkbox is selected, **Then** no correction run
   can be generated.
2. **Given** three selected and two unselected changes, **When** V2 is generated, **Then** V2 is
   linked to exactly the three selected change records and contains no line from the two unselected
   records.
3. **Given** an increase from 100 to 120 at the V1 price, **Then** V2 prices +20 using V1's price
   version, not a later price list.
4. **Given** a decrease from 100 to 80, **Then** V2 retains a signed -20 basis and a negative amount;
   it MUST NOT turn 80 into a second full invoice.
5. **Given** V1 is exported, **When** V2 is created, **Then** V1 remains immutable and readable and
   V2 is visibly labelled "Korrigering V2" for the same month.
6. **Given** V2 has blocking control findings, **Then** approval remains disabled exactly as for an
   ordinary run.
7. **Given** a correction is generated, **Then** its required reason, selected changes, author and
   time are audit-visible.

---

### User Story 3 - Continue with V3 without losing skipped changes (Priority: P2)

After V2 is exported, the user can compare another complete DWH snapshot—or recompare the retained
candidate—with the **effective invoiced basis**: V1 plus only the changes actually included in
exported corrections. A new correction becomes V3. Changes that were left unchecked in V2 remain
visible until they are included or explicitly dismissed with a recorded reason.

**Why this priority**: Versioning is only reliable if partial selection does not silently redefine
the baseline or make skipped differences disappear.

**Independent Test**: Leave one change unchecked in V2, export V2, then compare the same candidate
again. The unchecked difference is still shown; the exported V2 difference is no longer shown.
Generate and export it as V3 and verify the run chain V1 → V2 → V3.

**Acceptance Scenarios**:

1. **Given** V2 included only some candidate changes, **When** the candidate is compared after V2
   export, **Then** unselected changes remain as differences and selected changes do not.
2. **Given** V1 and exported V2, **When** another correction is generated, **Then** its version is V3
   and it links to V2 as the previous run in the chain.
3. **Given** a generated or approved correction is still in progress, **When** another comparison
   tries to generate a run, **Then** generation is blocked until the in-progress run is exported or
   discarded.
4. **Given** the invoiced baseline changed after a comparison page was opened, **When** the user
   submits an old selection, **Then** the request is rejected as stale and must be recomputed.
5. **Given** a change is explicitly dismissed, **Then** a reason is required and the dismissal is
   audit-logged; the source snapshot and change record remain retained.

---

### User Story 4 - Audit the complete correction chain (Priority: P2)

A FORVALTER, approver or auditor can open any run and see the month, version, whether it is ordinary
or a correction, the previous version, the candidate source snapshot, the selected changes and the
correction reason. Run lists and export filenames distinguish January V1, V2 and V3.

**Why this priority**: Several deliveries for one month are unsafe unless operators and auditors can
tell them apart and reconstruct why each amount was sent.

**Independent Test**: Open January V2 from the run list and follow links back to V1, the candidate
import and each selected old/new value. Download every V2 file and verify that its name contains
`2027-01-v2` and cannot be confused with V1.

**Acceptance Scenarios**:

1. **Given** several versions for a month, **Then** the run list orders them by month and version and
   shows type, status, net amount and change count.
2. **Given** a correction line, **Then** an auditor can trace it to the selected change, both old and
   new normalized values, the archived candidate payload and the previous run chain.
3. **Given** V2 export files, **Then** every filename and archive path contains the month and `v2`.
4. **Given** a discarded V2 attempt, **Then** it remains in history as discarded and its version is
   never silently reused.

### Edge Cases

- A new fetch arrives before the previous comparison is acted on: the old comparison is retained but
  marked superseded and cannot generate a run.
- The same raw payload is submitted twice against the same baseline: the existing comparison is
  returned instead of creating a duplicate.
- A source mapping change turns one old key into a removal plus a different added key; it is never
  guessed to be a rename.
- Old and new values differ only in decimal scale (`100` vs `100.00`): they are unchanged.
- A selected set has net amount zero: it is still a valid correction if at least one non-zero change
  is selected; positive and negative lines remain visible.
- A key changes to zero: it is a changed row; it is not silently treated as absent.
- All selected differences round to zero kroner at line level: generation is blocked with a clear
  message rather than creating an empty delivery.
- An ordinary run is not exported yet: the existing discard → re-import → regenerate flow remains
  the correction mechanism; a versioned correction is not offered.
- A candidate contains rejected rows or comes from the native forsystem CSV format: no correction
  comparison is created in the first increment. Only the DWH API and recognized complete DWH export
  are accepted, because absence must safely mean removal.
- The original run has an older data shape (for example no import link): comparison is blocked and
  explains that the historical baseline is insufficient; it never guesses.
- Registry data changed after V1: existing V1 invoice snapshots are reused where possible. Newly
  added usage that has no V1 billing resolution goes through current controls and may block V2.
- A negative correction reaches export before OQ-6 is answered and tt02-tested: production export is
  blocked; the signed basis remains available for review.

## Requirements *(mandatory)*

### Functional Requirements

**Candidate and comparison**

- **FR-001**: After a run for a period is EKSPORTERT, a FORVALTER MUST be able to fetch the period
  again from the DWH or upload a recognized complete DWH export as a correction candidate.
- **FR-002**: A candidate MUST pass the same completeness, mapping and row validation as a normal
  DWH import, and its exact raw payload and normalized rows MUST be archived immutably with checksum.
- **FR-003**: Creating a candidate MUST NOT replace the VALIDERT import, mutate an existing run, or
  change what is considered invoiced.
- **FR-004**: The system MUST compare the candidate to the effective invoiced basis by
  `(organisasjonsnummer, produkt, type)` using numeric equality independent of decimal scale.
- **FR-005**: The comparison MUST classify each non-zero difference as TILAGT, ENDRET or FJERNET and
  store old value, new value and signed difference. Unchanged rows MUST NOT be selectable.
- **FR-006**: The comparison MUST reconcile: for every key, `old + difference = new`; aggregate old,
  difference and new totals MUST reconcile by product and type.
- **FR-007**: Candidate creation MUST be idempotent by period, raw checksum and baseline run.

**Selection and correction run**

- **FR-008**: The comparison UI MUST offer an unchecked checkbox per change, a "Velg alle" control,
  filters for organisation/product/change type/sign, and a live selected count and signed totals.
- **FR-009**: Generating a correction MUST require at least one selected non-zero change and a
  non-blank reason.
- **FR-010**: A correction run MUST contain only selected differences and MUST retain a structural
  link from every invoice line to its selected change record.
- **FR-011**: The first run attempt for a period MUST be version 1 and ordinary. Every later ordinary
  retry or correction attempt MUST receive a monotonically increasing, never-reused version and link
  to the previous attempt; its comparison separately records the latest exported baseline.
- **FR-012**: Only one run in GENERERT or GODKJENT may exist per period. An EKSPORTERT earlier version
  MUST coexist with a correction draft.
- **FR-013**: Correction volume amounts MUST use the root V1 run's price version. Cost corrections
  MUST use the signed difference directly.
- **FR-014**: Existing V1 billing snapshots MUST be reused for reductions/removals and for additions
  to an already resolved customer/product where available. New billing resolutions MUST use current
  registry rules and the normal blocking controls.
- **FR-015**: A correction MUST use the existing GENERERT → GODKJENT → EKSPORTERT lifecycle,
  role checks, controls, reconciliation and audit behavior. Discarding a correction MUST NOT
  alter prior exported versions or the effective invoiced basis.
- **FR-016**: The system MUST reject selection from a comparison whose baseline is no longer current.

**Repeated versions and disposition**

- **FR-017**: The effective invoiced basis MUST equal V1's imported snapshot plus selected changes
  from EKSPORTERT correction runs only.
- **FR-018**: Unselected changes MUST remain pending when the candidate is recomputed. A user MAY
  explicitly dismiss one or more pending changes only with a reason; dismissal MUST be audited and
  MUST NOT delete the source or comparison.
- **FR-019**: A new candidate MUST supersede older open comparisons for the period without deleting
  them; superseded comparisons cannot generate a run.

**Export and audit**

- **FR-020**: Every run page, run-list row, download filename, archive filename and audit event MUST
  identify the month and version. Correction runs MUST also be labelled "Korrigering".
- **FR-021**: Positive and negative correction lines MUST be preserved through spreadsheets, PDFs,
  totals and LG04 reconciliation with exact signed sums.
- **FR-022**: The production Unit4 export representation for negative corrections MUST remain disabled
  until OQ-6 records the Økonomi + VIS decision and a representative tt02 import (positive, negative,
  removal and net-negative invoice) has succeeded. No transaction type, credit-note code or
  accounting value may be invented.
- **FR-023**: The LG04 batch identifier for multiple versions of one period MUST be unique and MUST use
  a format confirmed under OQ-6/OQ-23 before production enablement.
- **FR-024**: Audit history MUST record candidate creation, checksum, comparison baseline, superseding,
  selected/dismissed changes and reasons, correction generation, approval, discard and export.
- **FR-025**: The test-only *Nullstill periode* feature remains test-only and is not part of the
  production correction workflow.

### Key Entities

- **Correction candidate**: an immutable, complete normalized DWH snapshot for a closed period,
  linked to its archived raw payload; it is not the period's active normal import.
- **Usage comparison**: a stable comparison between one candidate and one effective invoiced
  baseline, with lifecycle OPEN, RUN_CREATED, SUPERSEDED or DISMISSED.
- **Usage change**: one changed natural key with old/new values, signed difference and change type.
- **Run version**: an ordinary or correction run's monotonically increasing version within a period,
  linked to its previous version and (for corrections) its comparison and selected changes.
- **Effective invoiced basis**: a computed state, not a mutable imported file: V1 plus changes from
  exported corrections.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a 10 000-row month with 20% changed keys, the comparison completes in under 10
  seconds in the load test and initially renders no more than 100 change rows per page.
- **SC-002**: For every comparison fixture, 100% of changed keys are classified correctly and every
  old + difference = new reconciliation is exact; unchanged keys never enter a correction run.
- **SC-003**: A user can go from a valid new DWH snapshot to a generated V2 with selected changes in
  under 5 minutes, excluding DWH transfer time.
- **SC-004**: Every V2+ invoice line is traceable to one selected usage change and both immutable
  source states; no correction line lacks that chain.
- **SC-005**: Replaying the same candidate is idempotent and creates zero duplicate comparisons,
  correction selections or invoice lines.
- **SC-006**: Before production enablement, tt02 successfully imports a correction fixture covering
  positive, negative, removed and net-negative cases, with Unit4 totals exactly equal to forsystem.
- **SC-007**: Existing ordinary V1 imports, runs and exports remain byte-identical and all existing
  tests pass; correction support changes no ordinary-run LG04 output.

## Assumptions

- The first increment accepts only a DWH API response or a recognized complete DWH export. The
  native forsystem CSV is not safe for removal detection because completeness is not guaranteed.
- A correction is offered only after at least one run for the period is EKSPORTERT. Before export,
  users keep the existing discard → re-import → regenerate flow.
- Selection is at the normalized usage key `(orgnr, product, type)`, not at raw daily-row level.
- Checkboxes start unchecked. "Velg alle" keeps the common all-changes case fast while preserving an
  explicit inclusion decision.
- Unselected changes stay pending. Dismissal is separate, explicit and requires a reason.
- Versions count immutable run attempts. If V2 is discarded, the next attempt is V3; V2 is not reused.
- This feature corrects usage only. Retroactive price, VAT, customer-master or accounting-code
  corrections remain outside scope and continue under their existing open questions.
- OQ-6 is a delivery gate, not permission to guess. The domain stores signed corrections regardless
  of whether Unit4 ultimately receives negative LG04 lines or a different confirmed mechanism.
