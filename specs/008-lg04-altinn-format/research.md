# Research: LG04 export in FEL's Altinn format

Sources: FEL's `test_3` sample (2026-11-04), FEL's written answers (2026-11-04), the LG04_55 field
spec (`LG04_55.xlsx`, 98 fields, 1-based positions), and regnskap's mass-invoicing example (Altinn,
May 2026). The two spreadsheets contain real customer and staff identifiers and are **not** copied
into the repo; only field definitions are referenced here.

## R1 — Order structure

- **Decision**: One order per invoice: a header line (`line_no` 0), then per product an article
  line and a text line, both carrying the product's `line_no` (1, 2, 3…). Built in `c344466`.
- **Rationale**: FEL's sample and regnskap's example both have this shape; the field spec says
  header lines have `line_no` 0 and article/text lines 1–9999.
- **Alternatives**: One order per invoice line (agresso/DPI) — rejected by FEL as the wrong format.

## R2 — Different references on one invoice (FR-009)

- **Decision**: Keep one order per distinct (fakturareferanse, bestillingsnummer) within an invoice,
  each with its own order number, and record an ADVARSEL `DELT_REFERANSE` on the invoice at
  generation, naming the references.
- **Rationale**: `accountable` (Deres referanse) and `ext_ord_ref` (Bestillingsnr) are header fields,
  so one order holds one pair. docs/01 special case 2 requires product-specific references. A
  BLOKKERENDE finding would block the whole run for one registry setting. FEL warned his own program
  does not do this and it is untested; the warning makes every split visible, and the December
  dry-run tests it with regnskap. Decided by the user 2026-11-04.
- **Alternatives**: Block the run (stops all invoicing); use the general reference and warn (wrong
  reference on the invoice).
- **Detection**: At generation, after an invoice's lines are sorted, count the distinct reference
  pairs on its lines (null counts as a value). More than one → finding. The export's grouping uses
  the same key, so finding and file always agree.

## R3 — VALUE_1 without quantity (FR-010)

- **Decision**: Blank when the line has no quantity (cost lines); `quantity × 100` rounded half-up
  otherwise, so quantity 0 is `0`.
- **Rationale**: Field 94 `value_1`: "Antall enheter. Hvis feltet ikke fylles ut, brukes
  standardverdien på 1.0", multiplied by 100. Regnskap's example uses quantity 1 for the
  infrastructure cost. FEL's `-100` is FEL's own marker for a failed import, not a Unit4 value.
- **Alternatives**: `-100` (FEL's marker; may show −1 on an invoice); `100` (equivalent to blank, but
  blank is the spec's own default and keeps the line identical to an amount-only line).

## R4 — Total incl. mva (FR-011)

- **Decision**: `sumInklMva = Σ over invoices of round_half_up(invoice.sumBelop × 1.25, 2)`. Shown
  next to the ex-mva total in the run summary and the e-mail block.
- **Rationale**: FEL: "Samlet beløp inkl. mva. er alle faktura-beløp ganget med 1,25", and to get
  the most exact figure each invoice should have its mva computed, because of øre rounding. Field 5
  `amount` is the net amount, so line amounts are ex. mva.
- **Alternatives**: Total × 1.25 (FEL warns it can differ from the real figure); per line (closer to
  Unit4 if Unit4 rounds per line, but FEL said per invoice). Small differences from Unit4's report
  remain possible and are noted on the page in the user guide.
- **Where**: `Kjoringssammendrag` (computed, never stored), so the summary table and the e-mail
  block share one number.

## R5 — Header and footer text width (FR-012)

- **Decision**: Pad-and-cut `long_info1` (offset 2509) and `long_info2` (offset 2629) to exactly 120
  characters.
- **Rationale**: Field spec: both are c120. Today the writer pads but does not cut, so a long value
  would overwrite `lot`, `main_apar_id` and later fields. Regnskap's template shows narrower layout
  limits (90 header, 65 footer) — that is about what fits on the printed invoice, out of scope
  until FEL/Økonomi decide footer content.

## R6 — Field positions confirmed by the spec

All positions the writer fills match LG04_55 (spec position − 1 = our offset): account 0, accountable
25, amount 212 (money, right-aligned), amount_set 232, apar_id 233, art_descr 538, article 793,
batch_id 881, client 906, cur_amount 965, dim_1 1908, dim_2 1933, dim_4 1983, ext_ord_ref 2368,
line_no 2493, long_info1 2509, long_info2 2629, order_id 3130, order_type 3145, responsible 3264,
responsible2 3289, sequence_no 3314, status 3410, terms_id 3519, trans_type 3944, value_1 4224,
voucher_type 4259. Noted for later (out of scope): `ext_ord_ref` is c100 though we cut at 25;
`sequence_no` 1–n means a numbered text line and FEL uses 8 with empty `short_info`; `text1` carries
the invoice to settle a credit note against; `unit_price` (4204) exists but is unused.
