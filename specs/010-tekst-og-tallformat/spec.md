# Feature Specification: Clearer texts, labels and number format

**Feature Branch**: `feat/010-tekst-og-tallformat` (stacked on `feat/009-kjoring-fakturatekster`)

**Created**: 2026-11-04

**Status**: Draft — ready for planning

**Input**: User description: "Clearer texts, labels and number format across forsystem, from Økonomi's
review in the project's Confluence kickoff page (2026-11): eksl. mva on every amount; one number
format (1 234 567,89); dimension labels Dim1 Koststed / Dim2 Prosjekt/formål / Dim4 Kappost; product
name in the bruksdata preview; a warning that a confirmed import cannot be undone; next-step pointers
on Bruksdata and Kjøringer; explanations of severities and control codes, «Eksempel» → «Beskrivelse»;
clearer Avstemming bruksdata (who imported, explanatory text, column names); invoice detail with
customer name, sum row and no per-line reference. UI only."

## Context

Økonomi tested forsystem in November 2026 and listed what confused them in the project's kickoff
page. Most items are wording, labels and number formatting. They need no business decision and
change nothing in calculations, controls or the LG04 file. This feature collects those items so they
reach the December dry-run together.

One item is not just wording. Users who signed in through Entra ID are recorded by an opaque
identifier, so "who imported" and the event log (hendelseslogg) show an id nobody recognises. This
feature records the readable username instead, from the next action onwards.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Amounts read the same way everywhere (Priority: P1)

Økonomi reads amounts on the run summary, the invoice list, one invoice's lines, the controls tab and
the bruksdata preview. Every amount is written as 1 234 567,89, and every count as 1 234 567. Every
labelled amount says it is excluding mva, so nobody wonders whether mva is included.

**Why this priority**: Økonomi compares these figures with Unit4 and the covering e-mail. A mix of
formats, or a figure that might include mva, causes wrong reconciliation.

**Independent Test**: Generate a run whose sums exceed 1 000 kr. Open every page listed below and
check each amount and count against the format, and each amount label for «eksl. mva».

**Acceptance Scenarios**:

1. **Given** an invoice whose sum is 1234567.891, **When** any page shows it, **Then** it reads
   «1 234 567,89».
2. **Given** a usage count of 1234567, **When** any page shows it, **Then** it reads «1 234 567»,
   with no decimals.
3. **Given** any table column or summary row that holds an amount, **Then** its label says
   «eksl. mva». The run summary's «inkl. mva» row is the only exception.

---

### User Story 2 - Importing usage is safe and leads to the next step (Priority: P1)

A FORVALTER fetches usage from the datavarehus, checks the preview and confirms. The preview names
products as the product registry does («Melding», not «melding»). Before each button that commits
data, a warning says the import cannot be deleted or changed afterwards. Once usage is imported, the
Bruksdata page points to Kjøringer as the next step. The Kjøringer page says usage must be imported
before a run can be generated.

**Why this priority**: A confirmed import is permanent (specs/001). Users must know that before they
click. They must also know where to go next without being told.

**Independent Test**: Walk the import flow from Bruksdata to Kjøringer, reading each page.

**Acceptance Scenarios**:

1. **Given** a preview of an import, **Then** each row shows the product's registry name. A product
   code with no matching product still shows the code, marked as unknown.
2. **Given** the preview, **Then** the text right before «Bekreft import» reads «Ved å trykke på
   Bekreft import importeres data fra datavarehuset. Importen kan ikke slettes eller endres etter at
   importen er bekreftet.»
3. **Given** the Bruksdata page, **Then** the text before «Hent fra datavarehus» says the same for
   fetching.
4. **Given** the Bruksdata page, **Then** it says the next step is Kjøringer, with a link.
5. **Given** the Kjøringer page, **Then** it says at the top that usage must be imported or fetched
   before an invoicing run can be generated, with a link to Bruksdata.

---

### User Story 3 - A reviewer understands every control finding (Priority: P1)

Before approving, a GODKJENNER reads the Kontroller tab. Next to the Alvorlighet and Kode column
headers there is a way to open an explanation. It covers what Blokkerende and Advarsel mean for
approval, and gives one short definition per control code. The column that shows a sample message is
called «Beskrivelse».

**Why this priority**: The approval decision depends on understanding the findings. A code such as
`DELT_REFERANSE` means nothing without its definition.

**Independent Test**: Open the Kontroller tab of a run with findings, open both explanations and
check that every code forsystem can raise is listed.

**Acceptance Scenarios**:

1. **Given** the Kontroller tab, **When** the user opens the Alvorlighet explanation, **Then** it says
   Blokkerende stops approval and Advarsel does not.
2. **Given** the Kontroller tab, **When** the user opens the Kode explanation, **Then** it lists every
   control code with a one-sentence definition and its severity.
3. **Given** the findings table, **Then** the fourth column is headed «Beskrivelse».
4. **Given** a run with no findings, **Then** the explanations are still available.

---

### User Story 4 - Avstemming bruksdata says what it checks and who imported (Priority: P2)

The Avstemming bruksdata section on the Kontroller tab explains that it checks only that all usage
reached the invoice basis, not prices or costs. Its columns name the datavarehus as the source. The
user who imported or fetched the usage is shown by a recognisable username.

**Why this priority**: Økonomi mistook this section for a cost reconciliation. Naming the source and
the purpose prevents that.

**Independent Test**: Open the Kontroller tab of a run generated from an import and read the section.

**Acceptance Scenarios**:

1. **Given** the section, **Then** it starts with: «Denne avstemmingen brukes for å kontrollere at
   alle bruksdata har kommet med på fakturagrunnlaget. Her hensyntas ikke prisliste eller kostnad som
   skal faktureres.»
2. **Given** the table, **Then** its columns read «Importerte rader fra datavarehus», «Importert
   bruksdata fra datavarehus», «Fakturerte linjer» and «Fakturert bruksdata».
3. **Given** a user signed in through Entra ID imports usage, **Then** the section names them by
   their username, such as `fornavn.etternavn@digdir.no`, not by an opaque id.

---

### User Story 5 - Products and invoice lines are easier to read (Priority: P2)

The product registry labels the dimension columns «Dim1 Koststed», «Dim2 Prosjekt/formål» and «Dim4
Kappost». One invoice's detail page shows the customer's name, has no per-line reference column, and
ends with a sum row.

**Why this priority**: These are small reading aids. Nothing else depends on them.

**Independent Test**: Open Produkter, and one invoice of a generated run.

**Acceptance Scenarios**:

1. **Given** no dimension names in configuration, **Then** Produkter shows «Dim1 Koststed», «Dim2
   Prosjekt/formål» and «Dim4 Kappost». Names set in configuration still replace them.
2. **Given** an invoice's detail page, **Then** the customer's name is shown with its organisation
   number and kundenummer.
3. **Given** that page, **Then** the lines table has no reference column, and its last row holds the
   sum of the line amounts, labelled eksl. mva and equal to the invoice's sum.
4. **Given** the invoice's reference, **Then** it is still shown once, with the invoice's other header
   facts.

### Edge Cases

- **Negative amounts**, from correction runs (specs/007): they keep their minus sign, e.g.
  «-1 234,50».
- **Zero**: it reads «0,00» for amounts and «0» for counts.
- **Unit prices below one øre**: an SMS price such as 0,0375 kr is shown in full. Rounding it would
  misstate the price list.
- **Fetching versus confirming**: «Hent fra datavarehus» only fetches and shows a preview. Its warning
  says that the import that follows cannot be deleted or changed once confirmed, not that fetching
  itself is permanent.
- **Very small amounts**: an amount such as 0,005 shows rounded to «0,01» on screen. The stored value
  and the LG04 øre amount are unchanged.
- **Older records**: imports and log rows made before this change keep the identifier they were
  stored with. Only new actions are recorded with the username.
- **Signing in without Entra ID**: in local development, the stub user's name is shown unchanged.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every amount shown on a page uses a space as the thousands separator, a comma as the
  decimal mark, and exactly two decimals. Unit prices (enhetspris, prices in a price version) use the
  same separators but keep every stored decimal, with at least two, so «0,0375» is never shown as «0,04».
- **FR-002**: Every count of rows, lines or units shown on a page uses a space as the thousands
  separator and has no decimals. Quantities that can be fractional (usage) are shown with two
  decimals, like amounts.
- **FR-003**: Every label of an amount (column header, summary row, sum text) says «eksl. mva»,
  except the run summary's incl.-mva row.
- **FR-004**: The product registry's dimension columns default to «Dim1 Koststed», «Dim2
  Prosjekt/formål» and «Dim4 Kappost». Configured names override the defaults.
- **FR-005**: The bruksdata import preview shows each product's registry name. A code with no product
  is shown as the code, marked unknown.
- **FR-006**: The import preview shows the given warning right before «Bekreft import». The Bruksdata
  page shows the equivalent warning right before «Hent fra datavarehus».
- **FR-007**: The Bruksdata page names Kjøringer as the next step, with a link. The Kjøringer page
  states that usage must be imported first, with a link to Bruksdata.
- **FR-008**: The Kontroller tab offers an explanation of both severities and of every control code,
  reachable from the Alvorlighet and Kode column headers. The list of codes is complete: a code added
  later without a definition fails the build.
- **FR-009**: The findings table's sample-message column is headed «Beskrivelse».
- **FR-010**: Avstemming bruksdata shows the given purpose text and the renamed columns.
- **FR-011**: When a user signs in through Entra ID, forsystem records the username claim, not the
  opaque subject identifier. This applies everywhere a user is recorded: the event log, imports, runs,
  approvals and exports.
- **FR-012**: An invoice's detail page shows the customer name, drops the per-line reference column,
  and ends the lines table with a sum row equal to the invoice's sum.
- **FR-013**: Nothing in this feature changes stored amounts, controls, approval rules or the LG04
  file. The golden-file test stays byte-identical.

### Key Entities

- **Kontrollkode**: each control code gains a short Norwegian definition shown to users. The
  definition lives next to the code, so the two cannot drift apart.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the pages listed in User Story 1, 100 % of amounts and counts follow FR-001/FR-002
  when checked against a run with sums over 1 000 000 kr.
- **SC-002**: 100 % of control codes forsystem can raise have a definition on the Kontroller tab.
- **SC-003**: A user who imports usage after this change is shown by a username a colleague
  recognises, on the Kontroller tab and in the event log.
- **SC-004**: Økonomi can mark Confluence kickoff rows 16, 17, 18, 26, 27, 28, 29, 33, 36, 37, the
  column part of 38, the formatting, sum, reference and customer-name parts of 46, and row 23 as ready
  for testing.
- **SC-005**: The LG04 golden-file test and every existing test still pass.

## Assumptions

- **Amount rounding**: «two decimals» means amounts are rounded half up to øre for display only, as
  forsystem already stores them in øre precision.
- **Username claim**: the username is Entra ID's `preferred_username` (normally the user's e-mail
  address). This is a configuration choice; a different claim can be set without code changes.
- **Imported usage counts** are whole numbers in practice, but the source format allows decimals, so
  they are shown with two decimals like amounts. Row and line counts are always whole.
- **Wording**: where Økonomi gave exact wording, it is used verbatim. Elsewhere the wording is ours
  and kept short.

## Out of scope

These need a decision first, or are separate features:

- Removing servicekode from invoice lines (waiting on the data warehouse owner).
- Removing the Azure cost wording from line descriptions (its source needs checking first).
- The text under the Avstemming bruksdata table (waiting on the data warehouse owner).
- Removing tilleggstekst from the invoice list: it is what tells apart customers who share one
  kundenummer (docs/01).
- Exporting lists to Excel/CSV/PDF, acknowledging control findings, removing the customer list,
  budget versions and cost reconciliation, the bagatellgrense, a missing-fakturareferanse control,
  filling bestillingsnummer from fakturareferanse, the full event log, session timeout, and the two
  navigation bugs on Kjøringer.
