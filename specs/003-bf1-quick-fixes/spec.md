# Feature Specification: Small fixes from Økonomi's BF1 test findings

**Feature Branch**: `003-bf1-quick-fixes`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "Quick fixes from Økonomi's BF1 test findings and the remaining small
control gaps: F06 (only active products when pricing), F05 (explain gyldig fra/til), F03 (sort
inactive products last), F01 (show dimensions in the product list), C-09 (warn about an active
customer with no usage), C-08 (log downloads of export files)."

## Context

Økonomi ran the first registry tests (BF1 T17–T19) and logged findings F01–F06 on the business
side's test page. Two of them they marked *Should* (F05, F06) and two *Could* (F01, F03). Our own
gap analysis of the same test plan added two small controls:

- C-09 (BF1 T10): an expected tjenesteeier is missing from the run.
- C-08 (BF1 T09/T14): who downloaded the LG04 file, and when.

These are small and independent of each other. They answer what Økonomi reported before the next
test check-in, and they close two control gaps before the December dry-run.

Out of scope, still waiting on answers sent to the business on 2026-10-12:

- reopening archived price versions (F04, Could)
- the meaning of each dimension (OQ-2)
- what the detail view and invoice should show (T11/T20)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Price a new version without inactive products in the way (Priority: P1)

A FORVALTER creates a new price version and sets unit prices. Only **active** products are listed,
because only active products need a price to activate the version. An inactive product appears only
if this version already has a price for it, marked "inaktiv", so no existing price is hidden.

**Why this priority**: This is BF1 finding F06 (Should). Today inactive products are listed with an
empty price field, which suggests they must be priced. They don't have to be, and pricing them is
confusing.

**Independent Test**: Deactivate a product, create a draft version, and check that the product is
not listed. Give an inactive product a price in an existing draft, and check that it is listed and
marked "inaktiv".

**Acceptance Scenarios**:

1. **Given** product "studio" is inactive, **When** a FORVALTER opens a new draft price version,
   **Then** "studio" is not in the price table, and every active product is.
2. **Given** an archived version that priced "studio" while it was active, **When** anyone opens that
   version, **Then** "studio" is listed with its price and marked "inaktiv".
3. **Given** a draft listing only active products, **When** the FORVALTER prices all of them and
   activates it, **Then** activation succeeds, as today.

---

### User Story 2 - Understand what "Gyldig fra / Gyldig til" does (Priority: P1)

A FORVALTER creating or reading a price version sees a short explanation. The dates decide which
billing periods use this version: a run for a period uses the one active version that covers the
period's first day; the dates are inclusive; two active versions cannot overlap; an empty "til"
means open-ended.

**Why this priority**: BF1 finding F05 (Should). Økonomi asked whether the interval is a technical
lock or just informational. It is a technical lock, and the screen should say so.

**Independent Test**: Open the price-version list and a version's page, and check that the
explanation is visible next to the date fields and on the version.

**Acceptance Scenarios**:

1. **Given** the price-version list, **When** a user looks at the "Ny prisversjon" form, **Then** a
   help text under the date fields explains that the dates decide which periods use the version.
2. **Given** an active version valid 2027-01-01 – open, **When** a user opens it, **Then** the page
   says in plain words which periods it is used for, e.g. "Brukes for kjøringer fra januar 2027 og
   fremover".
3. **Given** a version valid 2027-01-15 – 2027-12-31, **When** a user opens it, **Then** the page
   states that a run for January 2027 will *not* use it, because a period is matched on its first
   day (2027-01-01). The first period it covers is February 2027.

---

### User Story 3 - A tidier product list (Priority: P2)

On *Produkter*:

- active products come first and inactive ones are grouped at the bottom (F03)
- the table shows **Dim 1, Dim 2 and Dim 4** next to konto and artikkel-id, so nobody has to open
  "Rediger" to see the kontering (F01)

The column headings use labels that Økonomi can name (e.g. "Dim 1 (koststed)"). Until they have
confirmed the names, the headings say plain "Dim 1", "Dim 2" and "Dim 4".

**Why this priority**: BF1 findings F01 and F03 (both Could). It is quality of life, and nothing
breaks without it.

**Independent Test**: With one inactive product, open *Produkter*. The inactive product is listed
last, and every product's dimensions are visible in the table.

**Acceptance Scenarios**:

1. **Given** products melding (active), studio (inactive) and varsling (active), **When** a user
   opens *Produkter*, **Then** the order is melding, varsling, studio.
2. **Given** a product with dim 1/2/4 set, **When** a user opens *Produkter*, **Then** those values
   are visible in the table.
3. **Given** dimension names configured as "koststed" for Dim 1, **When** a user opens *Produkter* or
   a product's edit page, **Then** the heading and label read "Dim 1 (koststed)". With nothing
   configured, they read "Dim 1".

---

### User Story 4 - Warn when an expected tjenesteeier has no usage (Priority: P1)

When a run is generated, every customer with an **active agreement** that has **no usage** in the
period gets an ADVARSEL finding: "Kunde X (orgnr) har aktiv avtale, men ingen bruk i perioden". It
does not block approval. Økonomi decides whether the absence is expected.

**Why this priority**: BF1 T10 asks Økonomi to confirm that all expected tjenesteeiere are in the
fakturagrunnlag. Today nothing tells them who is *missing*. A missing tjenesteeier means lost
revenue that no other control catches.

**Independent Test**: Register two active customers. Import usage for only one of them and
generate. The run has exactly one new ADVARSEL, for the customer without usage, and it can still be
approved.

**Acceptance Scenarios**:

1. **Given** active customers A and B and usage only for A, **When** the run is generated, **Then**
   there is one ADVARSEL `AKTIV_KUNDE_UTEN_BRUK` naming B, and approval is not blocked by it.
2. **Given** customer C with avtalestatus INAKTIV and no usage, **When** the run is generated,
   **Then** no finding is raised for C.
3. **Given** a customer whose usage was all kept off the invoice (for example no kundenummer rule),
   **When** the run is generated, **Then** that customer gets the blocking finding as today, and
   *not* the "no usage" warning, because they had usage.

---

### User Story 5 - Know who downloaded the LG04 and when (Priority: P2)

Every download of an export file (LG04, PDF zip, CSV, XLSX) from a run's page is written to the
hendelseslogg: who, which run, which file type and which file. That way "has this file been sent
already?" can be answered after the fact (BF1 T09, T14).

**Why this priority**: The run is final after export, but the file can still be downloaded and sent
twice. Forsystem can't stop a person from emailing a file twice, but it can show who fetched it.

**Independent Test**: Export a run, download its LG04 twice, and check that the hendelseslogg shows
two `LASTET_NED` events for that run with type LG04.

**Acceptance Scenarios**:

1. **Given** an exported run, **When** a user downloads its LG04, **Then** the hendelseslogg records
   `LASTET_NED` for the run, with the file type, file name and user.
2. **Given** a file id that belongs to a *different* run than the one in the URL, **When** it is
   requested, **Then** the request is refused and nothing is logged.

---

### Edge Cases

- **A product that becomes inactive while a draft version exists**: the draft no longer lists it,
  unless it already has a price in that draft, in which case it stays visible as "inaktiv".
  Activation still only requires prices for active products (unchanged).
- **Price version with an empty "til"**: the explanation says "og fremover".
- **The first period of all (January 2027)**: the "no usage" warning still applies, because it does
  not depend on a previous period.
- **Many active customers with no usage** (e.g. the registry holds tjenesteeiere that rarely send
  anything): each gets one warning, and the warnings are listed together on the run page.
- **A customer that is only a deviating invoice recipient** (fakturamottaker for others) and has an
  active agreement but no usage of its own: it gets the warning. Økonomi can ignore it, and the
  message says "ingen bruk", not "feil".
- **Dimension names configured but a product has no dimension value**: the cell shows "—".

## Requirements *(mandatory)*

### Functional Requirements

**Price versions (F06, F05)**

- **FR-001**: A price version's page MUST list every active product. It MUST list an inactive
  product only when the version has a price for it, and then mark it "inaktiv".
- **FR-002**: The rules for setting prices and activating MUST NOT change. Activation still requires
  a price for every active product and no overlap with another active version.
- **FR-003**: The "Ny prisversjon" form MUST explain that the dates decide which billing periods use
  the version, as a technical rule, and how: the period's first day must fall within the dates, both
  dates are inclusive, an empty "til" is open-ended, and active versions cannot overlap.
- **FR-004**: A price version's page MUST state in words which periods it covers: the first covered
  month, and either the last covered month or "og fremover".

**Product list (F03, F01)**

- **FR-005**: *Produkter* MUST list active products first and inactive products last, each group
  ordered by code.
- **FR-006**: *Produkter* MUST show Dim 1, Dim 2 and Dim 4 for each product, with "—" where empty.
- **FR-007**: The labels for Dim 1, Dim 2 and Dim 4 MUST be configurable per environment and shown
  on the list and the edit page as "Dim n (name)". With no name configured, they show "Dim n" only.
  No dimension name may be hardcoded (OQ-2).

**Completeness control (C-09)**

- **FR-008**: Generation MUST add one ADVARSEL finding, `AKTIV_KUNDE_UTEN_BRUK`, for each customer with
  avtalestatus AKTIV that has no usage row in the run's usage import.
- **FR-009**: The new finding MUST NOT block approval.
- **FR-010**: The message MUST name the customer and its orgnr, and say that the customer has an
  active agreement but no usage in the period.

**Download logging (C-08)**

- **FR-011**: Every download of an export file MUST be written to the hendelseslogg as `LASTET_NED`
  for the run, with file type, file name and user.
- **FR-012**: A download request whose file does not belong to the run in the URL MUST be refused,
  and MUST NOT be logged as a download.

**Cross-cutting**

- **FR-013**: No change to LG04 output, the golden files, roles or the export flow.

### Key Entities

- **Kontrollkode `AKTIV_KUNDE_UTEN_BRUK`**: a new warning-level control finding, at run level, not
  tied to an invoice (the customer has none).
- **Dimension labels**: environment configuration for Dim 1, 2 and 4. This is display only; it is
  not stored and not used in LG04.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: BF1 findings F01, F03, F05 and F06 can be closed by Økonomi on their next test run.
- **SC-002**: In a run where an active tjenesteeier sent no usage, Økonomi can see who it is from the
  run page alone, without comparing lists by hand (BF1 T10).
- **SC-003**: For any exported run, the hendelseslogg answers "who downloaded the LG04, and when" for
  100 % of downloads made after this change.
- **SC-004**: Pricing a new version shows only the products that need a price, so the number of
  price fields equals the number of active products.

## Assumptions

- **Dimension names are configuration, not code**, and are empty by default. Økonomi's example
  "Dim1 = koststed" comes from a single finding, and Dim 2/Dim 4 are unknown (OQ-2). We don't guess.
  When Økonomi confirms the names, they are set per environment without a code change.
- **The no-usage control is a warning, not a blocker.** A tjenesteeier with an active agreement may
  legitimately have no billable usage in a month. Severity can be raised later if Økonomi wants that
  (proposed open question P-4 covers what must block).
- "Active agreement" means `avtalestatus = AKTIV` in the customer registry, the same definition the
  existing INAKTIV_AVTALE control uses (OQ-13 is still open on what AKTIV means in CRM).
- Reopening archived price versions (F04) is left out until Økonomi decides it is wanted. The safer
  "copy to new draft" option remains on the list (C-18).
- Downloads of the *pre-approval* spreadsheet are already logged (specs/002). This feature covers the
  archived export files.
