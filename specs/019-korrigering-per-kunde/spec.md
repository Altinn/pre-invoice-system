# Feature Specification: Corrections per customer — grouped deviations, reference changes, repeated corrections, credit and re-invoice

**Feature Branch**: `019-korrigering-per-kunde`

**Created**: 2026-11-24

**Status**: Draft — stories 1–4 ready for planning; stories 5–6 specified as seams, gated on OQ-6 and OQ-29

**Input**: Ticket from Økonomi about corrections (summarised in English; the ticket is the authority):

1. Import handling depends on where the period is in the invoicing process: before export, new data
   replaces what was loaded; after export, it is layered on top.
2. A list of deviations against the previous run, grouped by **customer**: customer name and U4
   kundenummer, with a «+» to expand the products under each customer.
3. Change categories: **Fjernet**, **Lagt til**, **Endret fakturareferanse**, **Endret
   bestillingsnummer**. A reference change has a transaction difference of 0 but must still show up.
4. Tick the customers to credit and re-invoice with the new information. Preferably a **full credit
   of the previous invoice plus a new invoice** with the corrected information («to kjøringer per
   endring»). Økonomi is unsure whether this is possible.
5. A field where the user can **manually enter the invoice number being credited** (its own field in
   LG04).
6. Several corrections can be run on the same dataset later, picking transactions that were not
   ticked or run before.

## Context

specs/007 built versioned correction runs: after a run is EKSPORTERT, a new complete DWH snapshot
becomes a *candidate*, is compared with the effective invoiced basis, and the user ticks changes to
generate V2, V3… containing **signed deltas only**. specs/018 made the DWH (CRM) the only customer
source; each import, and since PR #58 each candidate, stores its customer values per organisation
(name, kundenummer, fakturareferanse, bestillingsnummer).

Against the ticket, main has:

| Ticket item | State on main |
|---|---|
| 1. Replace before export, layer after | **Done.** Before export, re-import replaces (old import AVVIST); a GENERERT/GODKJENT run blocks import until discarded; after EKSPORTERT a re-fetch becomes a candidate. Not changed by this feature. |
| 2. Grouped by customer, name + U4 kundenummer, «+» | **Missing.** The list is flat, one row per (orgnr, produkt, type), orgnr only. |
| 3. Fjernet / Lagt til / Endret fakturareferanse / Endret bestillingsnummer | **Partial.** TILAGT/ENDRET/FJERNET on quantity/amount. A change with difference 0 counts as unchanged and is hidden; customer values are not compared. |
| 4. Tick customers; full credit + new invoice | **Missing by design.** Selection is per product row; V2 holds only deltas; a reference change cannot be a delta. |
| 5. Manual credited invoice number | **Missing.** |
| 6. Repeated corrections, unticked transactions later | **Partial.** Unticked changes reappear after the previous correction is exported, but only after a re-fetch/upload; no «compare again»; dismissal with a reason (specs/007 FR-018) has a column but no service or UI. |

Correction export is disabled in every environment (`forsystem.korrigering.eksport-enabled=false`,
OQ-6). No correction has reached Unit4, so the correction *mechanism* can still change without
migrating history.

This feature specifies what can be built now (stories 1–4) and the seams for what Økonomi + VIS
must decide first (stories 5–6). It never invents how Unit4 represents a credit, which LG04 field
holds the credited invoice number, or its format.

## Clarifications

### Session 2026-11-24 (from the ticket and existing decisions; business questions go to OQ-29)

- Q: What is a «customer» in the grouped list? → A: An organisation (orgnr). Since specs/018 an
  invoice is one orgnr with the DWH's one kundenummer, so customer = orgnr = one invoice per run.
- Q: Is the unit of selection the customer or the product row? → A: The customer, as the ticket
  says («krysse av for kundene»), and because a full credit + re-invoice can only be done per
  invoice. The «+» shows the products for review; they are not ticked one by one. Whether Økonomi
  ever needs partial selection within a customer is asked in OQ-29 (g).
- Q: Does the ticket's list of categories drop «changed quantity/amount»? → A: No category is
  dropped. The ticket names the categories the user must be able to see; a changed quantity or
  amount stays visible as **Endret** («Endret antall/beløp»). Confirmed or corrected under OQ-29 (f).
- Q: Must several corrections run in parallel? → A: No. The ticket asks for «several corrections
  on the same dataset later»; one correction in progress per period (specs/007 FR-012) stays.
- Q: Does versioning start at export or when a run is generated? → A: Unchanged for now: at export
  (ticket item 1 and the current code). Asked again in OQ-29 (d) because «avvik mot forrige
  kjøring» could also mean a generated, not yet exported run.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — See deviations per customer (Priority: P1)

A FORVALTER has fetched January again after V1 was exported. The comparison page lists one row per
**customer** that has at least one deviation: customer name, U4 kundenummer, orgnr, the categories
that apply (for example «Endret · Endret fakturareferanse»), the number of changed products and the
net amount difference. A «+» on each row expands the customer's changed products with old value,
new value and difference, as the flat list does today. Customers without deviations are counted
but hidden.

**Why this priority**: Økonomi works per customer: a customer is an invoice and a Unit4 kundenummer.
A flat product list makes them assemble that view by hand.

**Independent Test**: Exported V1 with three customers; a candidate where customer A has two changed
products, customer B one removed product and customer C nothing changed. The page shows two customer
rows (A with 2 products, B with 1), name and kundenummer from the candidate, C counted as unchanged;
expanding A shows its two product rows with correct old/new/difference.

**Acceptance Scenarios**:

1. **Given** a comparison with changes for several products of one customer, **When** the page
   opens, **Then** they appear under one customer row, collapsed, with name, U4 kundenummer, orgnr,
   categories, number of products and net difference in kroner.
2. **Given** a collapsed customer row, **When** the user activates «+», **Then** the products are
   shown with change category, type, old value, new value and difference; activating it again
   collapses them. It works with keyboard and screen reader (the control states expanded/collapsed).
3. **Given** a customer that exists only in the invoiced basis (all products removed), **Then** its
   name and kundenummer are those it was invoiced with, and the row is marked **Fjernet**.
4. **Given** a customer that exists only in the candidate, **Then** its row is marked **Lagt til**
   and shows the candidate's name and kundenummer.
5. **Given** filters for orgnr/name, kundenummer, product, category and sign, **Then** they filter
   customers (a customer matches if any of its products or customer-level changes match) and the
   page says how many customers are shown of how many.
6. **Given** more customers than fit on one page, **Then** paging is by customer, never splitting a
   customer's products across pages.

---

### User Story 2 — Reference changes show up even with difference 0 (Priority: P1)

CRM changed customer B's fakturareferanse after January was invoiced; the usage is the same. On the
comparison, B appears with category **Endret fakturareferanse**, the old and new reference side by
side, and a transaction difference of 0. The same holds for **Endret bestillingsnummer**.

**Why this priority**: The ticket says it explicitly. Today such a customer is invisible, so a
wrong reference on a sent invoice cannot be found from forsystem.

**Independent Test**: V1 invoiced customer B with reference «Ola Nordmann» and bestillingsnummer
«PO-1»; the candidate has identical usage, reference «Kari Nordmann» and bestillingsnummer «PO-2».
The comparison shows B with both categories, old → new for each, difference 0, and B is counted as
a deviation, not as unchanged.

**Acceptance Scenarios**:

1. **Given** identical usage and a changed fakturareferanse, **Then** the customer is listed with
   **Endret fakturareferanse**, «før» and «nå» values, and difference 0.
2. **Given** a changed bestillingsnummer, **Then** likewise with **Endret bestillingsnummer**.
3. **Given** both a reference change and a quantity change, **Then** the customer shows both
   categories once, and the product rows show the quantity change.
4. **Given** values that differ only in leading/trailing whitespace, or empty vs missing, **Then**
   it is not a change. Any other difference (including letter case) is a change, because it is
   printed on the invoice.
5. **Given** the old value is what the customer was **invoiced** with (the effective invoiced basis),
   **Then** a reference that changed in CRM and back again before the re-fetch is not a change.
6. **Given** a changed U4 kundenummer or name for the same orgnr, **Then** the customer is listed
   with **Endret kundenummer** or **Endret navn** so the change is never hidden; how it is to be
   invoiced is OQ-29 (f).
7. **Given** an identical candidate (usage and customer values), **Then** the page says nothing
   changed, as today.

---

### User Story 3 — Tick customers and generate the correction (Priority: P1)

The FORVALTER ticks customers (not product rows), sees the selected count and net difference,
writes the reason and generates the next version. Nothing is ticked by default; «Velg alle» ticks
every customer on the comparison. Until OQ-6/OQ-29 choose the mechanism, the correction is
generated as today (**delta**): each ticked customer contributes all its pending product changes.

**Why this priority**: The ticket's selection unit is the customer. It also prepares the full
credit + re-invoice (story 5), which can only work per invoice.

**Independent Test**: Tick customer A (two changed products) and leave B. Generate V2. V2 holds
exactly A's two product deltas; B's changes remain pending on the next comparison.

**Acceptance Scenarios**:

1. **Given** no customer ticked, **Then** no correction can be generated.
2. **Given** customer A ticked, **When** V2 is generated, **Then** V2 contains every pending product
   change of A and nothing from unticked customers; V2's selection records the customer and its
   product changes.
3. **Given** a ticked customer whose only deviations are customer-level (reference, kundenummer or
   name changes), **Then** delta generation is refused with: «Endret referanse kan ikke korrigeres
   med differanser. Det avventer avklaring med Økonomi (OQ-6).», naming the customers; the user can
   untick them or dismiss them (story 4).
4. **Given** a ticked customer with both a reference change and product changes, **Then** delta
   generation is likewise refused for that customer, because the delta invoice would carry the new
   reference while the invoice it corrects carried the old one.
5. **Given** the existing rules (reason required, stale baseline rejected, one correction in progress,
   all deltas rounding to 0 refused), **Then** they still apply.

---

### User Story 4 — Correct later: compare again and dismiss (Priority: P2)

After V2 is exported, the FORVALTER opens Bruksdata for January and presses **«Sammenlign på
nytt»**. Forsystem compares the latest candidate with the new effective invoiced basis (V1 + V2)
without a new fetch or upload. Customers left unticked in V2 are still listed; those corrected in V2
are not. A customer the FORVALTER decides not to correct can be **dismissed** with a reason; it
disappears from the selectable list, is shown under «Avvist» with who, when and why, and can be
restored while the comparison is open.

**Why this priority**: Ticket item 6. Today the user must re-fetch or re-upload, and the only way to
stop seeing a deviation is to correct it.

**Independent Test**: Candidate with customers A, B, C changed. Generate and export V2 with A. Press
«Sammenlign på nytt»: B and C are listed, A is not. Dismiss C with a reason; generate and export V3
with B; compare again: nothing pending, C listed under «Avvist».

**Acceptance Scenarios**:

1. **Given** an exported correction and a retained candidate, **When** the user presses «Sammenlign
   på nytt», **Then** a new comparison against the current effective basis is created, the old one is
   marked ERSTATTET, and no data is fetched or uploaded.
2. **Given** the latest correction run was discarded, **Then** «Sammenlign på nytt» is offered too,
   and the customers of the discarded run are pending again.
3. **Given** a correction is GENERERT or GODKJENT, **Then** «Sammenlign på nytt» is not offered and
   the page says which version is in progress.
4. **Given** the current comparison's baseline is already current, **Then** pressing it again returns
   the same comparison (idempotent), as for a re-submitted payload today.
5. **Given** a customer is dismissed, **Then** a reason is required, the dismissal is audit-logged
   with user, time, reason and the customer's changes, and the customer cannot be ticked.
6. **Given** a dismissed customer and «Sammenlign på nytt» on the **same** candidate, **Then** the
   customer stays dismissed if its changes are identical (same old and new values); if they differ,
   it is pending again.
7. **Given** a **new** candidate (new fetch/upload), **Then** earlier dismissals do not carry over;
   every deviation is pending.
8. **Given** a dismissed customer on an open comparison, **When** the user restores it, **Then** it
   is pending again and the restore is audit-logged.

---

### User Story 5 — Full credit and new invoice per ticked customer (Priority: P3, gated on OQ-6/OQ-29)

Once Økonomi + VIS confirm the mechanism, the FORVALTER can choose **«Krediter og fakturer på
nytt»** for a correction. For each ticked customer, forsystem produces a **credit invoice** that
reverses the customer's previously invoiced lines for the period in full, and a **new invoice** with
the candidate's complete usage and current customer values (references included). This is the only
way a reference-only change can be corrected.

**Why this priority**: Økonomi's preferred outcome, but they are unsure Unit4 supports it, and the
representation of a credit in LG04 is unknown (OQ-6). Building it before the answer would be
inventing accounting behaviour.

**Independent Test** (once enabled): V1 invoiced customer B 100 units at 2.00 with reference
«Ola». Candidate: 100 units, reference «Kari». Credit and re-invoice B: the correction holds a credit
invoice for B of −200.00 carrying V1's reference «Ola» and the credited invoice number, and a new
invoice of +200.00 carrying «Kari»; net 0; both trace to V1's lines and the candidate.

**Acceptance Scenarios** (the mechanism details are confirmed under OQ-6/OQ-29 before building):

1. **Given** the mechanism is not confirmed, **Then** the choice «Krediter og fakturer på nytt» is not
   offered, and the delta mode of story 3 is the only one.
2. **Given** it is confirmed and chosen, **Then** for every ticked customer the credit reverses
   exactly what the effective invoiced basis says was invoiced to that customer for the period
   (lines, quantities, amounts, kundenummer and references as invoiced), and the new invoice is
   built from the candidate as an ordinary invoice would be, priced at the root run's price version.
3. **Given** a credit and its new invoice, **Then** each links to the other and to the invoice(s) it
   credits, and the credit's net plus the new invoice's net equals the customer's selected delta.
4. **Given** OQ-29 (b) answers «two runs» (separate files or batches), **Then** the credit and the new
   invoice are delivered as the confirmed answer says; until then the domain keeps them as two linked
   invoices in one correction run.

---

### User Story 6 — Enter the invoice number being credited (Priority: P3, gated on OQ-6)

On each credit invoice, the FORVALTER enters, by hand, the Unit4 invoice number being credited
(«Fakturanummer som krediteres»). Forsystem does not know Unit4's invoice numbers; it is copied from
Unit4. The value is stored on the credit invoice, shown on its detail page and in the spreadsheet,
and written to the LG04 field VIS confirms.

**Why this priority**: The ticket asks for it, and OQ-6 records that Økonomi will need it. Its LG04
field («presumably `text1`», docs/07 OQ-6), format and whether it is mandatory are unconfirmed.

**Acceptance Scenarios** (field, format and rules confirmed under OQ-6 before building):

1. **Given** a credit invoice in a GENERERT correction, **Then** the FORVALTER can enter or change
   the credited invoice number; each change is audit-logged.
2. **Given** a credit invoice without a credited invoice number, **Then** approval is blocked by a
   BLOKKERENDE control (code and text to be added with the learning-center article), if OQ-6
   confirms the field is mandatory.
3. **Given** a value that does not fit the confirmed LG04 field, **Then** it is refused when entered,
   never shortened in the file.
4. **Given** the field is not yet confirmed, **Then** nothing is written to any LG04 offset; the code
   carries `TODO(OQ-6)` and the existing correction export gate stays closed.

### Edge Cases

- A customer whose changes are all on products the run could not invoice (for example missing
  price): shown like any other; generation reports the usual blocking control.
- A customer present in both bases whose candidate rows carry conflicting customer values
  (MOTSTRIDENDE_KUNDEDATA): shown with the conflict; ticking it gives the existing blocking finding.
- A removed customer with no customer values in the candidate: name, kundenummer and references come
  from what it was invoiced with (as specs/007 FR-014 and PR #58 do today).
- Invoiced references differ between a customer's lines (the old registry's product-specific
  references, before specs/018): the old value shown is the set of invoiced values; any value that
  differs from the candidate's counts as a change.
- An old run (before specs/018) without invoiced customer snapshots for some lines: the reference
  comparison for that customer is shown as «ukjent tidligere verdi» and never as unchanged.
- A customer exceeding one page of products when expanded: all its products are shown when expanded.
- Dismissing a customer with a pending reference change and product changes dismisses all of them;
  partial dismissal within a customer is not offered (same unit as selection).
- Version numbering, idempotency, superseding and stale-baseline rules are unchanged from specs/007.

## Requirements *(mandatory)*

### Functional Requirements

**Customer-level comparison (stories 1–2)**

- **FR-001**: The comparison MUST group changes by customer (orgnr) and show per customer: name,
  U4 kundenummer, orgnr, categories, number of changed products and net amount difference in kroner.
- **FR-002**: Name and kundenummer shown MUST come from the candidate's customer values; for a
  customer absent from the candidate, from the values it was last invoiced with.
- **FR-003**: Each customer row MUST have an expand/collapse control («+»/«−») revealing its product
  changes with category, type, old value, new value and difference; the control MUST be keyboard
  operable and expose its expanded state to assistive technology.
- **FR-004**: The comparison MUST compare, per customer, the invoiced fakturareferanse and
  bestillingsnummer (from the effective invoiced basis) with the candidate's values, after trimming
  whitespace and treating empty as missing. A difference MUST be recorded as **Endret
  fakturareferanse** or **Endret bestillingsnummer** with old and new value and a transaction
  difference of 0.
- **FR-005**: A changed U4 kundenummer or name for the same orgnr MUST be recorded and shown as
  **Endret kundenummer** / **Endret navn**; it MUST NOT be hidden.
- **FR-006**: Product-level categories stay **Lagt til**, **Endret** and **Fjernet** (specs/007).
  A customer-level **Lagt til**/**Fjernet** applies when every product of the customer is added or
  removed. A customer with any category MUST count as a deviation, never as unchanged.
- **FR-007**: The summary MUST show the number of customers with deviations, customers unchanged,
  and the counts per category.
- **FR-008**: Filters (orgnr/name, kundenummer, product, category, sign) and paging MUST work on
  customers; a page never splits one customer.

**Selection and delta generation (story 3)**

- **FR-009**: Selection MUST be per customer. Ticking a customer selects all its pending changes;
  per-product checkboxes are removed. «Velg alle» selects every pending customer of the comparison
  (all pages), and the selected count and net difference update live.
- **FR-010**: A correction's recorded selection MUST name the selected customers and every product
  and customer-level change included, linked to the comparison.
- **FR-011**: In delta mode, generation MUST be refused, naming the customers, if any ticked customer
  has a customer-level change (reference, kundenummer or name). The message MUST say this awaits
  OQ-6 and point to the learning-center article.
- **FR-012**: All specs/007 generation rules (reason, stale baseline, one run in progress, zero-krone
  refusal, price version of the root run, version numbering, audit) MUST still hold.

**Compare again and dismissal (story 4)**

- **FR-013**: When the period's latest run is EKSPORTERT or FORKASTET (no run in progress) and a
  candidate is retained, the comparison page and Bruksdata MUST offer «Sammenlign på nytt», which
  compares the latest candidate with the current effective invoiced basis without fetch or upload.
- **FR-014**: «Sammenlign på nytt» MUST be idempotent per (candidate, baseline) and MUST supersede
  the open comparison as a new fetch does today.
- **FR-015**: A FORVALTER MUST be able to dismiss one or more customers on an open comparison with a
  required reason, and restore a dismissed customer while the comparison is open. Dismissal and
  restore MUST be audit-logged with user, time, reason and the affected changes.
- **FR-016**: When a comparison is recreated from the **same** candidate, a dismissal MUST carry over
  for a customer whose changes are identical (same keys and same old and new values) and MUST NOT
  carry over otherwise. A new candidate MUST start with no dismissals.
- **FR-017**: Dismissed customers MUST NOT be selectable and MUST be listed separately with who,
  when and why.

**Credit and re-invoice seams (stories 5–6, gated)**

- **FR-018**: A correction run MUST record its method. Only **DELTA** is enabled. **KREDIT_OG_NY**
  MUST NOT be offered until OQ-6/OQ-29 (a, b, h) record the Økonomi + VIS decision and a
  representative tt02 import has succeeded.
- **FR-019**: The data recorded by FR-004/FR-010 (invoiced customer values per customer and the
  links from each change to the invoiced lines) MUST be sufficient to build a full credit of what
  was invoiced to a customer without reading anything that can change later.
- **FR-020**: The credited invoice number MUST NOT be given an LG04 offset, length, format or
  mandatory rule until OQ-6 confirms them; any placeholder is marked `TODO(OQ-6)`. The existing
  correction export gate MUST remain closed.

**Documentation**

- **FR-021**: The learning-center articles for corrections (`slik-gjor-du/lag-korrigeringskjoring`,
  `forsta-systemet/korrigering-og-avregning`) MUST be updated in the same pull request as each
  visible change (grouped list, categories, customer selection, «Sammenlign på nytt», dismissal,
  the delta refusal message).
- **FR-022**: `docs/07-open-questions.md` MUST carry the questions in OQ-29 and link OQ-6 to this
  spec.

### Key Entities

- **Customer deviation** (new): one customer (orgnr) on a comparison, with its invoiced and
  candidate customer values (name, kundenummer, fakturareferanse, bestillingsnummer), its
  customer-level categories, its product changes, its status (pending, selected into a run,
  dismissed) and, if dismissed, who/when/why.
- **Usage change** (specs/007): one product change; now belongs to a customer deviation.
- **Correction run** (specs/007): gains a method (DELTA; KREDIT_OG_NY reserved) and records selected
  customers.
- **Credit invoice** (reserved, story 5–6): an invoice in a correction that reverses a customer's
  invoiced lines, linked to the invoice(s) it credits, to its paired new invoice, and carrying the
  manually entered credited invoice number.

## Success Criteria *(mandatory)*

- **SC-001**: For a comparison fixture with 300 customers and 2 000 product changes, the first page
  of customers renders in under 2 seconds and expanding a customer shows its products at once.
- **SC-002**: In every comparison fixture, 100 % of reference and bestillingsnummer changes are
  listed, including those with difference 0; no customer with any category is counted as unchanged.
- **SC-003**: A FORVALTER can go from the comparison to a generated correction for chosen customers
  in under 3 minutes, without exporting to a spreadsheet to group by customer.
- **SC-004**: After a correction is exported, the remaining deviations can be compared again in one
  action, with zero re-fetches or uploads, and no unticked customer is lost.
- **SC-005**: Every dismissal and restore is in the audit log with a reason; no dismissed customer
  can enter a correction run.
- **SC-006**: Ordinary runs and their LG04 output stay byte-identical; all existing tests pass.

## Assumptions

- Customer = orgnr, and one customer is one invoice per run (specs/018). If OQ-28 brings back several
  kundenummer per orgnr, grouping moves to (orgnr, kundenummer).
- The candidate's customer values are what CRM says now; the invoiced values are those on the
  exported invoice lines (fakturareferanse, bestillingsnummer, kundenummer snapshots).
- Item 1 of the ticket is already done and unchanged here; whether versioning should start before
  export is OQ-29 (d).
- One correction in progress per period remains; the ticket asks for corrections in sequence, not in
  parallel.
- Correction export stays behind the existing gate until OQ-6 is answered; stories 1–4 are useful
  without it (review, dry-run, preparing Økonomi's decision).

## Open questions for Økonomi / VIS (recorded as OQ-29 in docs/07)

- **(a) Mechanism**: full credit of the previous invoice + new invoice, or a delta invoice (only the
  difference, positive or negative)? One for all cases, or chosen per correction?
- **(b) «To kjøringer per endring»**: should the credit and the new invoice be in the same LG04 file,
  in two files, or in two separately approved runs? In which order must Unit4 receive them?
- **(c) Credited invoice number**: which LG04 field (presumably `text1`, OQ-6), its length and
  format, whether it is mandatory on every credit, and whether one credit can settle several
  invoices.
- **(d) Versioning start**: corrections start after **export** today (before export, a re-import
  replaces). Should a generated but not exported run also count as «forrige kjøring»?
- **(e) Reference-only change**: should it be invoiced as full credit + new invoice with the same
  amounts, corrected directly in Unit4, or not corrected at all?
- **(f) Categories**: the ticket lists Fjernet, Lagt til, Endret fakturareferanse, Endret
  bestillingsnummer. Is «Endret antall/beløp» wanted (assumed yes)? How should a changed kundenummer
  or name be handled?
- **(g) Granularity**: is the customer always the unit, or must individual products be corrected or
  dismissed?
- **(h) «Previous invoice»**: if a customer has been invoiced more than once for the period (V1 and a
  later correction), does the full credit reverse all of them, only the latest, or one credit per
  invoice, each with its own credited invoice number?
- **(i) Price**: the new invoice uses the root run's price version (as delta corrections do). Confirm.
