# Feature Specification: Fakturareferanse control and bestillingsnummer fallback

**Feature Branch**: `claude/012-fakturareferanse-3dd86d`

**Created**: 2026-11-05

**Status**: Draft — ready for planning

**Input**: Økonomi's kickoff list, two rows: «Kjøringer, kontroller: Trenger kontrollfunn på manglende
Fakturareferanse (=Deres referanse) (tilsvarende Aktiv_kunde_uten_bruk). Mulighet for å få opp liste over
kunder som mangler fakturareferanse», and «Har behov for at forsystemet automatisk legger Bestillingsnummer
= Fakturareferanse/Deres referanse dersom feltet Bestillingsnummer kommer tomt fra CRM/import fra
datavarehus.»

## Context

Every invoice line is snapshotted at generation with a fakturareferanse («Deres referanse») and a
bestillingsnummer from the customer registry (`kunde_referanse`; a product row overrides the customer
default, docs/01 special case 2). The LG04 order header carries both, 25 characters each. Today a
missing fakturareferanse passes silently (docs/07 OQ-15 deferred the control), and an empty
bestillingsnummer reaches Unit4 empty. FEL says the two are usually the same. If the lines of one invoice
carry different reference pairs, the export splits the invoice into one LG04 order per pair and
generation warns with `DELT_REFERANSE` (specs/008).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which invoices lack «Deres referanse» (Priority: P1)

Before approving January 2027, Økonomi opens the Kontroller tab. Among the findings is an advarsel
`MANGLER_FAKTURAREFERANSE` for each invoice with a line without fakturareferanse, naming the customer,
orgnr, kundenummer and products. Below the findings, «Fakturaer uten fakturareferanse» lists the same
invoices and can be downloaded as XLSX or CSV, so Økonomi can get the references from the customers
(or CRM) and regenerate.

**Why this priority**: An invoice without «Deres referanse» is often returned by the customer's
accounts payable. The first production run is in February 2027.

**Independent Test**: Generate a run with one customer without references and one with; only the first
gets the warning and a row in the list; approval is not blocked.

**Acceptance Scenarios**:

1. **Given** an invoice whose lines have no fakturareferanse, **When** the run is generated, **Then**
   it has one ADVARSEL `MANGLER_FAKTURAREFERANSE` with customer name, orgnr, kundenummer and the
   products, and approval is not blocked.
2. **Given** a customer with two kundenumre, **Then** each invoice gets its own finding and row.
3. **Given** a product row with only a bestillingsnummer, **Then** that product's line counts as
   missing (the product row replaces the default; OQ-24) and the list says which product.
4. **Given** a customer left out under the threshold, or an active customer without usage, **Then** it
   has no finding and no row — it gets no invoice this month.
5. **Given** the list, **When** downloaded as CSV or XLSX, **Then** it has one row per invoice with
   ordre, kundenummer, kundenavn, orgnr, products, line count and sum; the download is logged.
6. **Given** every invoice has a fakturareferanse, **Then** the tab says so and offers no download.

---

### User Story 2 - Bestillingsnummer defaults to the fakturareferanse (Priority: P1)

A customer's reference comes from CRM or the datavarehus with a fakturareferanse but no
bestillingsnummer. Its invoice gets the fakturareferanse in both fields, in the lines, the
spreadsheets, the PDFs and the LG04 header. The registry keeps what was delivered.

**Why this priority**: Økonomi's explicit wish; customers match the invoice on bestillingsnummer.

**Independent Test**: Register `REF-A` with no bestillingsnummer, generate and export: the LG04 header
has `REF-A` at both offsets (25 and 2368), and the registry still has an empty bestillingsnummer.

**Acceptance Scenarios**:

1. **Given** a reference with fakturareferanse `REF-A` and an empty (or blank) bestillingsnummer,
   **Then** every line it applies to has bestillingsnummer `REF-A`.
2. **Given** a bestillingsnummer, **Then** it is kept.
3. **Given** a fakturareferanse that is empty, **Then** nothing is filled in either direction.
4. **Given** two lines `REF-A`/empty and `REF-A`/`REF-A`, **Then** they are one LG04 order and there is
   no `DELT_REFERANSE` — the fallback never adds a split.
5. **Given** lines with different fakturareferanser, **Then** the split and the warning stay, each
   order with its own fakturareferanse as bestillingsnummer.
6. **Given** the customer page, **Then** an empty bestillingsnummer next to a fakturareferanse reads
   «— (fakturareferansen brukes)».

### Edge Cases

- Correction runs get the same fallback and warning: their invoices also go to Unit4.
- Runs generated before this feature keep their lines as they were; the list reads the lines, so it is
  correct for old runs too (they simply have no `MANGLER_FAKTURAREFERANSE` finding).
- A fix in the registry takes effect when the run is discarded and regenerated, as for any finding.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Generation MUST record one ADVARSEL `MANGLER_FAKTURAREFERANSE` per invoice with at least one
  line without fakturareferanse, naming customer, orgnr, kundenummer and products.
- **FR-002**: The code MUST be declared with severity and definition so the Kontroller tab explains it.
- **FR-003**: The Kontroller tab MUST list the run's invoices with lines lacking fakturareferanse
  (customer, orgnr, kundenummer, products, sum, link to the lines).
- **FR-004**: The list MUST be downloadable as CSV and XLSX; downloads are logged.
- **FR-005**: When a line's bestillingsnummer is empty or blank, generation MUST snapshot the
  fakturareferanse as bestillingsnummer. The registry MUST NOT be changed.
- **FR-006**: The fallback MUST NOT increase the number of LG04 orders for any invoice.
- **FR-007**: The LG04 golden file MUST stay byte-identical.
- **FR-008**: docs/07, the user guide, the feature list and the decision log MUST describe the behaviour.

### Key Entities

- **Kontrollfunn**: new code `MANGLER_FAKTURAREFERANSE` (ADVARSEL, per invoice).
- **Fakturalinje**: unchanged columns; `bestillingsnummer` now holds the fallback.

## Success Criteria *(mandatory)*

- **SC-001**: 100 % of invoices with a line without fakturareferanse are visible before approval.
- **SC-002**: 0 invoices reach Unit4 with an empty bestillingsnummer when the fakturareferanse is set.
- **SC-003**: 0 extra invoice splits caused by the fallback.

## Assumptions

- «Tilsvarende Aktiv_kunde_uten_bruk» means the same severity: ADVARSEL, not blocking (OQ-15 keeps the
  mandatory-field list open; raise to BLOKKERENDE if it says so).
- «Kunder som mangler fakturareferanse» means the customers on this run's invoices; a registry-wide list
  is not built until Økonomi asks (OQ-24).
- A product row with an empty fakturareferanse does not inherit the customer default (existing rule;
  OQ-24).
- No reverse fallback (fakturareferanse from bestillingsnummer): not asked for.
