# Feature Specification: Invoice threshold (terskelverdi)

**Feature Branch**: `feat/011-terskelverdi`

**Created**: 2026-11-05

**Status**: Draft — ready for planning

**Input**: Økonomi's kickoff list: "Det er besluttet at terskelverdi for å sende faktura er 40 kr eks
mva per kunde per måned. Har behov for å få dette bygget inn i forsystemet. Ønsker også mulighet for å
endre terskelverdi i forsystemet på naturlig plassering (ikke noe som skal gjøres ofte)", and "Kjøringer,
Fakturaer: Ønsker å ha mulighet til å se og hente ut liste over ikke-fakturerte ordre som faller bort
pga. terskelverdi … Begge listene skal kunne eksporteres til excel, csv, pdf osv."

## Context

FEL's program for DPI/eSignering only invoices customers whose total for the month is over 40 kr. On
2026-11-04 FEL confirmed (docs/07 OQ-22) that the same applies under FinMod: 40 kr ex. mva, summed per
organisasjonsnummer across all Altinn products, invoicing only totals strictly over 40 kr; FEL's
program drops the smaller amounts, and FEL suggests making the threshold adjustable. Økonomi's
kickoff list records the decision. Today forsystem invoices every customer with usage, however small.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Small customers are not invoiced (Priority: P1)

A FORVALTER generates January 2027. A customer with 23,50 kr of usage in total gets no invoice; a
customer with 40,01 kr does. The run shows one warning saying how many customers were left out and
for how much in total, and the usage reconciliation explains every left-out usage row.

**Why this priority**: It decides who gets an invoice in the first production run. Sending 20-kroner
invoices costs more than it brings in, and it is what Økonomi decided.

**Independent Test**: Generate a run with customers at 23,50, 40,00 and 40,01 kr; only the last is
invoiced, and the LG04 file has only its order.

**Acceptance Scenarios**:

1. **Given** a threshold of 40 kr and a customer whose total across all products and kundenumre is
   40,00 kr or less, **When** the run is generated, **Then** the customer gets no invoice and its
   usage is not in the file.
2. **Given** a customer at 40,01 kr spread over two kundenumre (20,00 + 20,01), **Then** both its
   invoices are made — the total per customer decides, not each invoice.
3. **Given** customers left out, **Then** the run has one ADVARSEL `UNDER_TERSKEL` naming how many
   customers and the total amount, and approval is not blocked.
4. **Given** customers left out, **Then** Avstemming bruksdata lists their usage as not invoiced,
   explained by `UNDER_TERSKEL`, and reports no invariant breach.
5. **Given** a correction run (specs/007), **Then** no threshold is applied — differences are
   invoiced as before.

---

### User Story 2 - See and export what is left out (Priority: P1)

Before approving, Økonomi opens the Fakturaer tab. Below the invoices is a second list: «Faktureres
ikke (under terskelverdi)», one row per would-be invoice with customer, organisasjonsnummer,
kundenummer, amount and the customer's total. Both lists can be downloaded as CSV and Excel.

**Why this priority**: Økonomi has to be able to check — and explain to a customer — why there was no
invoice.

**Independent Test**: Generate a run with one customer under the threshold; the Fakturaer tab shows
it in the second list, and the CSV/XLSX downloads contain the same rows.

**Acceptance Scenarios**:

1. **Given** customers left out, **When** the Fakturaer tab opens, **Then** they are listed under
   «Faktureres ikke (under terskelverdi)» with the threshold that applied and the total amount.
2. **Given** no customers left out, **Then** the tab says that no customer was under the threshold.
3. **Given** either list, **When** downloaded as CSV or XLSX, **Then** the file has one row per
   invoice (or would-be invoice) with the same figures; amounts are numbers in Excel; the download
   is logged.

---

### User Story 3 - Økonomi sets the threshold with the price list (Priority: P2)

The threshold belongs to the price model, so it is set on the price version (Prisversjoner): a new
version starts at 40,00 kr, and a FORVALTER can change it while the version is an UTKAST. From
activation on it is locked, like the prices, so a period is always generated with the same rule.

**Why this priority**: Changing it is rare; the default covers 2027.

**Independent Test**: Create a price version, change its threshold to 50,00, activate it, try to
change it again (refused), generate: a customer at 45 kr is left out.

**Acceptance Scenarios**:

1. **Given** a new price version, **Then** its threshold is 40,00 kr.
2. **Given** an UTKAST, **When** a FORVALTER saves 50,00, **Then** it is stored and logged with old
   and new value.
3. **Given** a negative or empty threshold, **Then** it is refused.
4. **Given** an AKTIV or ARKIVERT version, **Then** the threshold is shown read-only and a change is
   refused.
5. **Given** the price versions that exist when the feature is installed, **Then** they get 40,00 kr.

### Edge Cases

- A customer with only zero lines (total 0,00) is under any threshold ≥ 0 and is left out, without the
  NULLBELOP warning its invoice would have had.
- A customer with blocking findings (e.g. missing price) is not affected differently: the run cannot
  be approved until the finding is solved and the run regenerated.
- A customer left out this month and invoiced next month gets the NY_KUNDE warning next month (it was
  not invoiced in the previous run).
- Amounts at or under the threshold are dropped, not carried to the next month, as FEL's program does.
  Whether Økonomi wants carry-over is still open (OQ-22).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each price version MUST have a threshold in kroner ex. mva, default 40,00 for new
  versions; existing versions get 40,00 when the feature is installed.
- **FR-002**: The threshold MUST be editable (≥ 0, two decimals) only while the version is UTKAST;
  every change is logged.
- **FR-003**: When an ordinary run is generated, a customer (organisasjonsnummer) whose total over all
  its would-be invoices is not greater than the active version's threshold MUST get no invoice.
- **FR-004**: The run MUST record one ADVARSEL `UNDER_TERSKEL` when customers are left out, with the
  number of customers, their total and the threshold; the usage rows of those customers MUST be linked
  to it, so the usage reconciliation explains them.
- **FR-005**: The run MUST keep a list of the left-out would-be invoices (customer, orgnr, kundenummer,
  amount, number of lines, customer's total), shown on the Fakturaer tab.
- **FR-006**: The invoice list and the left-out list MUST be downloadable as CSV and XLSX; downloads
  are logged.
- **FR-007**: Correction runs MUST NOT apply the threshold.
- **FR-008**: Left-out customers MUST NOT appear in the LG04 file, the PDFs, the spreadsheets of
  invoice lines or the run's sums.
- **FR-009**: docs/07 OQ-22, the user guide and the feature list MUST describe the threshold.

### Key Entities

- **Prisversjon**: gains `terskelverdi`.
- **Faktura under terskel**: a would-be invoice left out of a run, linked to the run, the customer and
  the `UNDER_TERSKEL` finding.

## Success Criteria *(mandatory)*

- **SC-001**: 0 customers with a monthly total of 40,00 kr or less are invoiced in an ordinary run.
- **SC-002**: Every left-out customer is visible before approval, in the list and in one warning.
- **SC-003**: The usage reconciliation of a run with left-out customers reports no unexplained rows.
- **SC-004**: Changing the threshold takes one form on an UTKAST price version and is impossible on an
  active one.

## Assumptions

- Per customer means per `kunde` (organisasjonsnummer in the customer registry), as FEL sums per orgnr.
- Amounts under the threshold are forgiven (dropped), as in FEL's program; carry-over is out of scope
  until Økonomi asks for it (OQ-22).
- PDF export of the lists is out of scope; CSV and XLSX cover the request ("excel, csv, pdf osv.") for
  filtering and sorting. The page can be printed.
- Changing the threshold for an already active year means a new price version, like changing a price.
