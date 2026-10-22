# Feature Specification: Digdir look and feel with Designsystemet

**Feature Branch**: `004-designsystemet-ux`

**Created**: 2026-10-22

**Status**: Implemented (2026-10-22)

**Input**: User description: "Improve the UX of the app, starting with Digdir Designsystemet
(https://github.com/digdir/designsystemet). Use Digdir colours, fonts and everything else the design
system offers. Read the docs and implement properly what we have not implemented yet."

## Context

forsystem already loads Designsystemet's stylesheet and a Digdir theme, but only partly follows it:

- **Outdated stylesheets.** The vendored stylesheet is an older release. The Digdir theme comes from a
  package that upstream has deprecated, and its colour and size tokens date from v1.8.
- **No font.** The theme names Inter as the font, but the font is never loaded and the page never
  selects it. Every page renders in the browser's serif fallback (Times), which does not look like a
  Digdir service.
- **Some components are hand-built or missing.**
  - The header is a plain row of links that doesn't show where the user is.
  - "← Kunder"-style back links stand in for breadcrumbs.
  - Hints sit in brackets inside field labels.
  - Alerts are one-line paragraphs.
  - Irreversible actions run on a single click with no confirmation.
- **Inline spacing.** About 50 inline style attributes set spacing by hand instead of using the
  design system's spacing tokens.

The people affected are the Digdir FORVALTER, Økonomi (approvers), and reviewers in the BF1 test
rounds. They use the tool monthly, under time pressure, to approve real invoices. A familiar Digdir
look builds trust, and clearer structure means fewer wrong clicks.

Out of scope: page content, wording of business rules, business logic, the LG04 file, the API, and
the Digdir logo (see Assumptions).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The app looks like a Digdir service (Priority: P1)

A user opens any page and sees the current Digdir visual identity:

- the Inter typeface
- Digdir's blue accent colour and neutral tones
- Digdir's corner radius, spacing and type scale

Every page shows these consistently, and nothing renders in a fallback font.

**Why this priority**: This is the request itself, and the most visible gap: every page currently
renders in a serif fallback font. It is also the foundation the other stories build on.

**Independent Test**:

1. Open the start page, a list page, a form and a detail page.
2. Check that text renders in Inter, primary buttons use Digdir blue, and colours match the current
   Digdir theme.
3. Check that no page depends on fonts or stylesheets from the internet.

**Acceptance Scenarios**:

1. **Given** a machine with no internet access running the app locally, **When** a user opens any
   page, **Then** all text renders in Inter at regular, medium and semibold weights.
2. **Given** any page, **When** it is inspected, **Then** colours, radius and spacing come from the
   current Digdir theme, not a deprecated one.
3. **Given** the stylesheet upgrade, **When** each existing page is opened, **Then** every existing
   component still renders correctly: buttons, inputs, tables, tags, cards and alerts.

---

### User Story 2 - Know where I am and get around quickly (Priority: P1)

The header shows the product name and the main sections. The current section is clearly marked, both
visually and for screen readers.

Detail pages show breadcrumbs (for example *Kjøringer › Kjøring 2027-01*) instead of a bare
"← Kjøringer" link.

Keyboard users can skip straight to the main content.

**Why this priority**: The monthly cycle moves between six sections. Today nothing shows which
section you are in, and back links lose the trail.

**Independent Test**: Visit each section and one detail page per section. Check three things:

- the header marks the active section
- breadcrumbs show the path and every level is clickable
- the first Tab press offers a "Hopp til hovedinnhold" link

**Acceptance Scenarios**:

1. **Given** the user is on any page under Kunder, **When** they look at the header, **Then**
   "Kunder" is marked as the current section, and assistive technology announces it as current.
2. **Given** a kjøring detail page, **When** it loads, **Then** breadcrumbs show
   Forside › Kjøringer › Kjøring 2027-01. Every level except the current page is a link.
3. **Given** a keyboard user on any page, **When** they press Tab once, **Then** a visible link to
   skip to the main content appears, and activating it moves focus to the main content.
4. **Given** a narrow window, about phone width, **When** the page is shown, **Then** the header
   navigation wraps or condenses, and nothing scrolls sideways except wide tables.

---

### User Story 3 - Forms explain themselves and say what is wrong (Priority: P2)

Form fields show:

- a short label
- a description of the expected format, as a separate hint line, not in brackets in the label
- whether the field is required or optional, using the design system's convention: a tag, never an
  asterisk

If the browser rejects a value, for example an organisation number that is not 9 digits, a
Designsystemet-styled message appears under the field in Norwegian, instead of the browser's native
popup.

When the server rejects an action, the error page shows a clear heading, the reason, and a button
back to where the user came from.

**Why this priority**: Wrong input in registry forms ends up on invoices. Clear hints and error
messages prevent mistakes, but they matter less than the identity and navigation stories.

**Independent Test**:

1. Open "Ny kunde".
2. Check that each hint is a separate description line and that optional fields are marked
   "Valgfritt".
3. Submit an 8-digit organisation number and check that the message appears under the field.
4. Trigger a business-rule error and check the error page.

**Acceptance Scenarios**:

1. **Given** the "Ny kunde" form, **When** it is shown, **Then** "Organisasjonsnummer" has the
   description "9 siffer", and "Fakturamottaker orgnr" is tagged "Valgfritt", with its explanation
   as a description.
2. **Given** a field with a format rule, **When** the user submits an invalid value, **Then** the
   form is not sent, a message under the field explains the rule, and the field is announced as
   invalid.
3. **Given** a business-rule rejection from the server, **When** the error page is shown, **Then**
   it has a heading, the rule message in an error alert, and a "Tilbake" button.

---

### User Story 4 - Confirm before irreversible actions (Priority: P2)

A user who clicks **Godkjenn**, **Forkast** or **Eksporter** on a run first sees a confirmation
dialog. The dialog names the action and the period, and says what happens next. Only the
confirmation button inside the dialog performs the action, and Escape or "Avbryt" cancels it.

**Why this priority**: These actions change invoices or files sent to accounting. A mis-click is
expensive, and Constitution VII says money is approved only by an explicit human action.

**Independent Test**: On a generated run:

1. Click Forkast and check that a dialog appears and the run is unchanged.
2. Click Avbryt and check that nothing happens.
3. Confirm, and check that the run is discarded as before.

**Acceptance Scenarios**:

1. **Given** a GENERERT run, **When** the user clicks "Godkjenn", **Then** a dialog asks them to
   confirm approval of that period's run, and the run stays GENERERT until they confirm.
2. **Given** the dialog is open, **When** the user presses Escape or clicks "Avbryt", **Then** the
   dialog closes and nothing is submitted.
3. **Given** the dialog is open, **When** the user confirms, **Then** the action runs and the result
   is the same as today.
4. **Given** approval is blocked by blocking findings, **When** the page is shown, **Then** the
   "Godkjenn" button stays disabled as today.

---

### User Story 5 - Tables, statuses and messages are easy to scan (Priority: P2)

Tables are easy to scan:

- rows are highlighted on hover
- numeric and amount columns are right-aligned
- digits have equal width, so amounts line up
- each table has a heading or caption

Statuses show as coloured tags. Alerts that carry a finding have a heading and body text instead of
one bold line. Empty lists say so plainly and, where useful, say what to do next. Long help text
(for example the CSV format explanation) is folded away under an expandable section.

**Why this priority**: The control pages (avstemming, kontrollfunn, fakturaer) are what Økonomi
reads before approving. Faster scanning means faster and safer approval.

**Independent Test**:

1. Open a run with invoices and findings.
2. Check that amounts line up on the decimal comma and that hovering a row highlights it.
3. Check that each blocking finding is a danger alert with the code as its heading.
4. Open the bruksdata page and check that the format help is collapsible.

**Acceptance Scenarios**:

1. **Given** a run with invoices, **When** the invoice table is shown, **Then** the "Sum" column is
   right-aligned with equal-width digits, so values line up.
2. **Given** a blocking finding, **When** it is shown, **Then** it appears as a danger alert with the
   finding code as heading and the message as body.
3. **Given** a list with no rows, **When** it is shown, **Then** a single plain message explains
   that the list is empty, and where relevant, how to add the first entry.

---

### User Story 6 - Follow my system's light or dark mode (Priority: P3)

The app follows the operating system's light or dark preference. In dark mode every page stays
readable, with the contrast the theme provides.

**Why this priority**: This is a comfort feature. Designsystemet supports it at little cost, but
nobody has asked for it.

**Independent Test**: Switch the OS to dark mode and open the start page, a table page, a form and
a dialog. Check that the backgrounds are dark and that the text and controls are legible.

**Acceptance Scenarios**:

1. **Given** the OS prefers dark mode, **When** any page loads, **Then** it renders with the dark
   colour scheme of the Digdir theme.
2. **Given** dark mode, **When** a page with custom layout (header, cards, tables) is shown,
   **Then** no element keeps a hard-coded light colour.

### Edge Cases

- **Partial page swaps.** The app swaps page content without full reloads (boosted links and
  forms). The active section, breadcrumbs, dialogs and validation must still work after such a
  swap, not only after a full reload.
- **Browsers without native dialog support.** Older browsers that lack native dialog commands
  still get a working confirmation, through the vendored design-system scripts.
- **JavaScript off or failed.** If scripts fail to load, irreversible actions must still work as
  plain form submissions. No action may become unreachable.
- **Downloads.** File downloads (LG04, PDF, CSV, XLSX) keep working, and none of them goes through
  a confirmation dialog.
- **Long names.** Long customer names and file names wrap inside tables and do not break the layout.
- **Printing.** A printed page, for example the email figures, has no header navigation or dialog
  chrome.

## Requirements *(mandatory)*

### Functional Requirements

**Identity and foundation**

- **FR-001**: The UI MUST use the current release of Designsystemet's stylesheet and the current
  official Digdir theme. It MUST NOT use the deprecated theme package.
- **FR-002**: The UI MUST render all text in Inter at 400, 500 and 600 weights, served by the
  application itself. Pages MUST NOT load fonts, stylesheets or scripts from the internet
  (portability, Constitution V).
- **FR-003**: Layout spacing, radius, colour and shadow MUST use design-system tokens. Ad-hoc inline
  style attributes in templates MUST be replaced by a small shared stylesheet built on those tokens.
- **FR-004**: Pages MUST declare Norwegian Bokmål as their language, so built-in component texts
  and screen readers use Norwegian.

**Navigation**

- **FR-005**: Every page MUST have a common header with the product name, linking to the start
  page, and links to the six main sections. The section containing the current page MUST be
  marked as current, both visibly and programmatically.
- **FR-006**: Every page MUST start with a skip link to the main content.
- **FR-007**: Every page below a section MUST show breadcrumbs from the start page to the current
  page. They replace the existing "← X" back links.

**Forms and errors**

- **FR-008**: Field hints MUST be shown as a description under the label, not in the label text.
- **FR-009**: Required and optional fields MUST follow the design system's pattern: if all fields
  in a form are required, a single sentence says so; otherwise, optional fields carry a
  "Valgfritt" tag. Asterisks MUST NOT be used.
- **FR-010**: When browser-side validation fails, a Norwegian message MUST appear under the field
  and the field MUST be marked invalid. The message clears when the value becomes valid.
- **FR-011**: The business-rule error page and the technical error page MUST show a heading, the
  message in an error alert, and a "Tilbake" button.

**Confirmations**

- **FR-012**: The run actions Godkjenn, Forkast and Eksporter MUST ask for confirmation in a modal
  dialog before submitting. So MUST the price-version actions Aktiver and Arkiver, which are just as
  irreversible. The dialog MUST name the action and what it applies to, and MUST offer confirm and
  cancel. Cancel has the initial focus.
- **FR-013**: If scripts are unavailable, those actions MUST still submit as before. The
  confirmation is an enhancement, not a gate.

**Tables, statuses and messages**

- **FR-014**: Data tables MUST highlight rows on hover. Numeric and amount columns MUST be
  right-aligned with equal-width digits.
- **FR-015**: Control findings and other multi-part messages MUST render as alerts with a heading
  and body. One-line notices MAY stay as single-line alerts.
- **FR-016**: Empty lists MUST show a plain empty-state message.
- **FR-017**: Help text longer than about three lines MUST be collapsible.

**Colour scheme and print**

- **FR-018**: Pages MUST follow the OS light/dark preference.
- **FR-019**: Header, navigation and dialogs MUST NOT appear on printouts.

**Behaviour and testing**

- **FR-020**: Business behaviour MUST stay unchanged: every existing route, form field name, action
  and download, and the LG04 output. All existing tests MUST keep passing.
- **FR-021**: An automated test MUST check, for every main page:
  - the shared header
  - the marked current section
  - the skip link
  - the language
  - that no external resource is referenced

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pages render in Inter, with no network requests to third-party hosts.
- **SC-002**: Inline style attributes in templates fall from about 50 to 0.
- **SC-003**: On every main section and its detail pages, a first-time user can tell which section
  they are in and get back up a level in one click.
- **SC-004**: None of the three irreversible run actions completes from a single accidental click.
- **SC-005**: Automated accessibility checks of the start page, a list, a form, a detail page and
  the run page report no contrast or labelling errors, in both light and dark mode.
- **SC-006**: All existing automated tests pass unchanged, and the LG04 golden files stay
  byte-identical.

## Assumptions

- **No Digdir logo yet.** The Digdir logo guidelines don't say whether the logo may be used in
  internal tools, so the header shows the product name as text. Adding the logo is a follow-up once
  Digdir's communications team confirms. That is a brand question, not a docs/07 business value.
- **Stylesheet source.** The current Digdir theme is taken from the design system's own repository,
  because the published theme package is deprecated. All assets stay vendored in the application,
  as today.
- **Validation stays where it is.** Server-side validation still reports through the error page.
  Inline per-field messages from the server would need controller changes, which are out of scope.
  Browser-side checks (required, format) get inline messages.
- **Colour scheme follows the OS.** There is no in-app light/dark toggle.
- **No new pages and no content changes.** Page texts change only where a hint moves from a label
  to a description, or where an empty state or dialog needs wording.
