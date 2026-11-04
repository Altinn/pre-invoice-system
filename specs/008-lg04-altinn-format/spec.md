# Feature Specification: LG04 export in FEL's Altinn format

**Feature Branch**: `feat/lg04-altinn-format`

**Created**: 2026-11-04

**Status**: Draft — ready for planning

**Input**: User description: "LG04 export in FEL's Altinn format. The spec covers the commit already
on the branch (one LG04 order per invoice with a header line and one line pair per product; FEL's
test_3 as the byte-exact golden file; buntnummer and område in the e-mail figures) and the
follow-ups settled by FEL's answers of 2026-11-04 and the LG04_55 field spec: no splitting by
reference, VALUE_1 empty on lines without quantity, total incl. 25 % mva in the e-mail figures,
header and footer text capped at 120 characters, and docs/07 updated with FEL's answers."

## Context

The LG04 file is how forsystem's approved fakturagrunnlag reaches Unit4: a FORVALTER downloads it
and e-mails it to regnskap in VIS, who import it (OQ-8). Until now forsystem wrote the layout
ported from `../agresso`, which is the DPI/eSignering format: one order per invoice *line*, so a
customer using six products would get six invoices. On 2026-11-04 the FEL contact said the file had
the wrong format and sent an Altinn sample (`test_3`) "som vil være det format som skal brukes",
followed by the field spec (`LG04_55.xlsx`) and regnskap's own mass-invoicing example.

The sample settles the order structure (OQ-1) and most field values (OQ-23). FEL's follow-up
answers settle five more points: the unit code is dropped for good; `-100` in VALUE_1 is only
FEL's own import-error marker; the link in the footer is not clickable in the invoice PDF; the
total incl. mva is the invoice sum × 1.25; and FEL advises against splitting one customer's invoice
by reference until invoicing runs.

The first part of this feature is already built on the branch (commit `c344466`). This spec
records it and adds the follow-ups, so the whole change is reviewed and released as one.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The LG04 file is what Unit4 expects for Altinn (Priority: P1) — built in c344466

After approving January's run, a FORVALTER exports it and sends the LG04 file to regnskap. Each
customer invoice is one order in the file: a header line carrying the customer number, references
and order data, followed by one pair of lines per product on the invoice. Regnskap imports it and
Unit4 produces one invoice per customer with all its products, matching FEL's Altinn sample.

**Why this priority**: The file is the only thing that reaches Unit4. A file in the DPI layout
gives customers one invoice per product, which is wrong for FinMod.

**Independent Test**: Render FEL's sample order (one customer, Altinn studio and Autorisasjon) and
compare the whole file byte for byte with FEL's `test_3` (person name anonymised).

**Acceptance Scenarios**:

1. **Given** one invoice with two products, **When** the run is exported, **Then** the file has
   five lines: a header with line number 0, then a pair numbered 1 and a pair numbered 2.
2. **Given** several invoices, **When** exported, **Then** each invoice is exactly one order, and
   each order's lines share the same order number.
3. **Given** a product line with quantity 152 and amount 503.65 kr, **Then** the amount line
   carries 50365 øre right-aligned in both amount fields, the product name as article text, the
   article number, and VALUE_1 = 15200.
4. **Given** any export, **Then** every line is exactly 4324 characters, Windows-1252, and the
   pair's second line carries sequence number 8.
5. **Given** an export on 16 October for the September run, **Then** the buntnummer on every line
   is 20261016 and the header text reads "Bruk av Altinn september 2026".

---

### User Story 2 - Cost lines carry no false quantity (Priority: P1)

Applikasjonsinfrastruktur is invoiced as an amount, with no quantity. On the invoice it must show
as one unit at that amount, which is what the field spec defines when VALUE_1 is left empty, and
what regnskap's own example does.

**Why this priority**: The built version writes `-100` (FEL's error marker) for such lines, which
could show as a quantity of −1 on a customer's invoice.

**Independent Test**: Export an invoice with a cost line (amount, no quantity) and check that
VALUE_1 on its amount line is blank while the amount fields carry the amount.

**Acceptance Scenarios**:

1. **Given** a cost line with an amount and no quantity, **When** exported, **Then** VALUE_1 is
   empty (spaces).
2. **Given** a volume line with quantity 0, **When** exported, **Then** VALUE_1 is `0`, not
   `-100` and not empty.
3. **Given** a volume line with quantity 2.5, **Then** VALUE_1 is `250`.

---

### User Story 3 - The e-mail figures match what regnskap sees (Priority: P2)

After export, the run page shows the figures for the covering e-mail to regnskap: område, buntnummer,
periode, number of invoices, and the total both ex. and incl. mva. Regnskap compares the incl. mva
total with Unit4's import report, so it is computed the way FEL advises: each invoice's sum × 1.25,
rounded to øre, then summed.

**Why this priority**: Regnskap reconciles the import against these figures. The current page marks
the total "mva uavklart", which is no longer true.

**Independent Test**: Export a run with two invoices whose ex-mva sums are 10.01 kr and 10.01 kr;
the page shows 20.02 kr ex. mva and 25.02 kr incl. mva (12.51 + 12.51), not 25.03 (20.02 × 1.25 =
25.025, rounded).

**Acceptance Scenarios**:

1. **Given** an exported run, **When** a LESER opens it, **Then** the e-mail block shows "Område:
   Altinn", the buntnummer written in the file, the period, the number of invoices, the total ex.
   mva and the total incl. mva.
2. **Given** invoice sums whose mva rounds differently per invoice than in total, **Then** the incl.
   mva total is the sum of the per-invoice rounded amounts.
3. **Given** any run page, **Then** no "mva uavklart" label remains.

---

### User Story 4 - Different references on one invoice are handled visibly (Priority: P2)

A customer can have a fakturareferanse/bestillingsnummer that applies to one product only (docs/01,
special case 2). In LG04 the reference sits on the order's header line, so one order can carry only
one reference. When one invoice's lines carry different references, forsystem must not silently
pick one or silently produce something FEL has not tested.

**Why this priority**: It affects only customers registered with product-specific references, but
when it happens the result is either a wrong reference on the invoice or a structure Unit4 has not
seen.

**Independent Test**: Register a customer with one general reference and a different reference for
one product, generate a run with usage on both products: the control page shows an ADVARSEL for
that invoice, and the exported file has two orders for the customer, each with its own reference.

**Acceptance Scenarios**:

1. **Given** an invoice whose lines all carry the same reference (or none), **Then** it is one order
   and no finding is recorded.
2. **Given** an invoice whose lines carry different references, **When** the run is generated,
   **Then** an ADVARSEL finding on that invoice names the references, and the run can still be
   approved.
3. **Given** that invoice is exported, **Then** the file has one order per reference, each with its
   own header carrying that reference and only the lines that carry it.

---

### Edge Cases

- An invoice with more than nine products: the line number grows to two digits within its 4-wide
  field; the reader accepts it.
- A header or footer text longer than 120 characters: it is cut at 120 and never spills into the
  next field.
- A quantity with more than two decimals: VALUE_1 is rounded half-up to whole hundredths.
- An exported run read back later from the archive: the reconciliation still counts orders and sums
  amounts from the file alone, independently of the writer.
- A run exported before this change (DPI layout, generation date as buntnummer): its archived file
  is not rewritten; the page's buntnummer is the export date, which may differ for such old runs.
- Correction runs (007): their export stays disabled (OQ-6) and is not affected.

## Requirements *(mandatory)*

### Functional Requirements

Already built (c344466):

- **FR-001**: The LG04 export MUST write one order per invoice: a header line with line number 0,
  then one pair of lines per invoice line, numbered 1, 2, 3… in the invoice's line order.
- **FR-002**: Every line of an order MUST carry the same order number, the buntnummer, the header
  text and the footer text.
- **FR-003**: The amount line MUST carry the amount in øre right-aligned in both 20-wide amount
  fields, the product name as article text, the product's article number and kontering, and
  VALUE_1 = quantity × 100.
- **FR-004**: The second line of each pair MUST carry the fixed sequence number 8.
- **FR-005**: The buntnummer MUST be the date the file is exported (yyyyMMdd); the header text MUST
  be "Bruk av Altinn" followed by the run's period in Norwegian ("september 2026").
- **FR-006**: FEL's `test_3` sample, with the person name replaced by a same-length placeholder,
  MUST be the golden file, and the export MUST reproduce it byte for byte.
- **FR-007**: Before anything is archived, the export MUST read the file back with a reader that
  does not share the writer's code, and stop if the order count or øre sum differs from the run.
- **FR-008**: The e-mail figures MUST show område (Altinn) and the file's buntnummer.

Follow-ups:

- **FR-009**: When one invoice's lines carry different fakturareferanse/bestillingsnummer, the
  export MUST write one order per distinct reference pair (each with its own order number, lines
  in the invoice's order), and generation MUST record an ADVARSEL finding on that invoice naming
  the references and saying the invoice becomes several invoices in Unit4. The run is not blocked.
  (Decided 2026-11-04: keeps docs/01 special case 2 working; blocking would stop the whole run for
  one registry setting. FEL has not tested this case, so it is verified in the December dry-run.)
- **FR-010**: On a line with no quantity, VALUE_1 MUST be left empty. A quantity of zero MUST be
  written as `0`.
- **FR-011**: The e-mail figures MUST show the total ex. mva and the total incl. mva, where the
  incl. mva total is the sum over invoices of (invoice sum × 1.25, rounded half-up to øre). The
  "eksl./inkl. mva uavklart" labels MUST be removed.
- **FR-012**: The header text and footer text MUST be cut to 120 characters, so they can never
  overflow into the following field.
- **FR-013**: `docs/07-open-questions.md` and the decision log MUST record FEL's answers of
  2026-11-04: unit code dropped for good; applikasjonsinfrastruktur gets article 20009 (unit KR in
  Unit4); `-100` is FEL's error marker; the footer link is not clickable in the PDF; the
  bagatellgrense is per organisasjonsnummer and chosen by Økonomi; mva = sum × 1.25; the FR-009
  decision and that the split is untested by FEL.

### Key Entities

- **LG04 order**: one per invoice. Header values: kundenummer, fakturareferanse, bestillingsnummer,
  order number, buntnummer, header text, footer text, responsible persons. Holds an ordered list of
  product lines.
- **LG04 product line**: one per invoice line. Account, article number, dimensions, product name,
  amount in øre, quantity (absent for cost lines).
- **E-mail figures**: område, buntnummer, period, invoice count, total ex. mva, total incl. mva.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For FEL's sample order, the exported file is identical to FEL's `test_3` in every byte
  (apart from the anonymised name).
- **SC-002**: A customer with N products on one invoice appears as exactly one order with N product
  pairs, unless the invoice carries several references (FR-009), in which case every split is
  shown as a warning before approval.
- **SC-003**: No exported line ever carries `-100` as VALUE_1.
- **SC-004**: The incl. mva total on the run page equals the sum of the per-invoice incl. mva
  amounts to the øre.
- **SC-005**: The December 2026 dry-run file is accepted by regnskap's Unit4 import without format
  errors.
- **SC-006**: All existing automated tests stay green, with the golden-file test as the format
  contract.

## Assumptions

- Invoice line amounts are ex. mva and every Altinn product carries 25 % mva (FEL, 2026-11-04).
  Unit4 may round per line; the per-invoice figure is the closest forsystem can get, and small øre
  differences from Unit4's report are expected.
- The footer keeps today's configured per-invoice link until FEL/Økonomi say what it should hold.
- The unit code stays out of the file; units come from the article in Unit4.

### Out of scope (waiting on FEL / Økonomi)

- A text line with sequence 1 and `short_info` (e.g. tilleggstekst) instead of sequence 8.
- Kontering per product (3247 vs 3030 and dimensions): registry data, not code (OQ-2).
- The 40 % påslag on applikasjonsinfrastruktur seen in regnskap's example.
- Footer content, the `responsible` user, credit notes via `text1` (OQ-6; FEL does not use `text1`
  today).
- Widening bestillingsnummer from 25 to the field's 100 characters — settled: FEL keeps both at 25
  (2026-11-04), and the registry now refuses longer values.
- The bagatellgrense itself (OQ-22), which is a separate task.
