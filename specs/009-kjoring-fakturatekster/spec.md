# Feature Specification: Invoice texts per invoicing run

**Feature Branch**: `feat/009-kjoring-fakturatekster` (cut from `feat/lg04-altinn-format`, specs/008)

**Created**: 2026-11-04

**Status**: Draft — ready for planning

**Input**: User description: "Let Økonomi edit three invoice texts per invoicing run
(fakturakjøring), prefilled with defaults and stored with the run so a re-export writes the same
text: header (LG04 long_info1 @2509, default «Bruk av Altinn <måned år>»), footer (long_info2
@2629, default «Se samarbeid.digdir.no for priser og innsyn i egen statistikk»), Vår referanse
(responsible/responsible2 @3264/3289, default Servicedesk@altinn.no). Edited on the run page before
approval; LG04 widths enforced (120/120/25); printed-invoice limit (90/65 per regnskap's template)
unconfirmed TODO(OQ-23); editing blocked once approved; every change audited."

## Context

Every Altinn invoice in Unit4 carries three texts that forsystem puts in the LG04 file: a header
above the lines, a footer below them, and «Vår referanse» (who the customer contacts). Until now
the header was fixed («Bruk av Altinn <måned år>»), the footer was a per-invoice link from
configuration — which FEL reports is not clickable in the invoice PDF — and Vår referanse was a
placeholder person from configuration (OQ-2).

On 2026-11-04 Økonomi answered (docs/07 OQ-23) that the three texts are specified in the project's
kickoff page: each must be editable per invoicing run, prefilled with a default. Økonomi gave the
defaults. FEL has confirmed the LG04 fields hold 120 characters (header, footer) and 25 (Vår
referanse); regnskap's invoice template suggests 90 for the header and 65 for the footer on the
printed invoice, which FEL has not confirmed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The run carries sensible texts without anyone touching them (Priority: P1)

A FORVALTER generates January 2027. The run page shows the three invoice texts, already filled in:
header «Bruk av Altinn januar 2027», footer «Se samarbeid.digdir.no for priser og innsyn i egen
statistikk», Vår referanse `Servicedesk@altinn.no`. A GODKJENNER approves and exports; every order
in the LG04 file carries those three texts.

**Why this priority**: Most months nobody will change the texts. The defaults must be right and
must reach the file without manual steps.

**Independent Test**: Generate a run, export it, and read the header, footer and both responsible
fields from every line of the file.

**Acceptance Scenarios**:

1. **Given** a newly generated run for 2027-01, **When** the run page opens, **Then** it shows the
   header «Bruk av Altinn januar 2027», the default footer and the default Vår referanse.
2. **Given** that run is approved and exported unchanged, **Then** every line carries the header at
   the header field, the footer at the footer field, and Vår referanse in both responsible fields.
3. **Given** a correction run (specs/007) is generated, **Then** it gets the same defaults for its
   period.

---

### User Story 2 - Økonomi adjusts a text before approval (Priority: P1)

Before approving March, Økonomi wants the footer to mention a price change. A FORVALTER edits the
footer on the run page and saves. The page shows the new text, the run's history shows who changed
which text from what to what, and the exported file carries the new footer.

**Why this priority**: This is the point of the feature — the texts are Økonomi's to set per month.

**Independent Test**: Edit the footer of a generated run, then check the page, the history and the
exported file.

**Acceptance Scenarios**:

1. **Given** a generated run, **When** a FORVALTER saves a new footer, **Then** the run stores it,
   the page shows it, and the history records the field, the old text and the new text.
2. **Given** only the footer was changed, **Then** the history records only the footer, not the
   unchanged header and Vår referanse.
3. **Given** the form is saved with no changes, **Then** nothing is recorded in the history.
4. **Given** an edited run is exported, **Then** the file carries the edited text; exporting again
   after a stopped export writes the same text.

---

### User Story 3 - Texts that would not fit are refused before they reach the file (Priority: P1)

A FORVALTER pastes a long paragraph into the header. The page refuses it with a message that names
the field and its limit, and keeps the run's stored text. Next to each field the page shows how many
characters the LG04 field holds and how many regnskap's template suggests for the printed invoice.

**Why this priority**: The LG04 file is contractual (constitution II). A text that is cut silently,
or that breaks the fixed-width line, is a wrong invoice.

**Independent Test**: Submit a 121-character header, a 26-character Vår referanse, a text with a
line break, and a text with a character Windows-1252 cannot hold; each is refused and the stored
texts are unchanged.

**Acceptance Scenarios**:

1. **Given** a header of exactly 120 characters, **When** saved, **Then** it is accepted.
2. **Given** a header or footer of 121 characters, or a Vår referanse of 26, **When** saved,
   **Then** it is refused with the field's name and limit, and no text of the run changes.
3. **Given** a text with a line break or tab, **When** saved, **Then** it is refused.
4. **Given** a text with a character Windows-1252 cannot represent (e.g. an emoji), **When** saved,
   **Then** it is refused and the message names the character. Norwegian letters (æ ø å) are
   accepted.
5. **Given** an empty or whitespace-only text, **When** saved, **Then** it is refused.
6. **Given** a header of 100 characters (above regnskap's suggested 90), **When** saved, **Then** it
   is accepted, and the page warns that it may be too long on the printed invoice (unconfirmed,
   OQ-23).

---

### User Story 4 - Texts are frozen once the run is approved (Priority: P1)

After a GODKJENNER approves February, the texts are part of what was approved. The run page shows
them read-only, and an attempt to change them (e.g. a stale form or a direct request) is refused.

**Why this priority**: Humans approve money (constitution VII). What is approved must be what is
exported.

**Independent Test**: Approve a run, then submit a text change; it is refused and the stored texts
and history are unchanged. Repeat for an exported and a discarded run.

**Acceptance Scenarios**:

1. **Given** an approved, exported or discarded run, **When** the run page opens, **Then** the texts
   are shown without a form.
2. **Given** an approved, exported or discarded run, **When** a text change is submitted, **Then**
   it is refused with a message that the texts are locked after approval, and nothing changes.

---

### Edge Cases

- Leading and trailing spaces are trimmed before validation and storing; the limit counts the
  trimmed text.
- A run generated before this feature and still awaiting approval or export gets the defaults when
  the feature is installed, so it can be exported. Runs already exported or discarded keep no texts
  (they were written with the old per-invoice link and configured responsible); their page says the
  texts were not stored.
- A discarded run's texts do not carry over: regenerating the period starts from the defaults again.
- Two FORVALTERs saving at once: the last save wins, and both changes are in the history.
- A Vår referanse of up to 25 characters fills the responsible fields exactly; it can never spill
  into the following field.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every invoicing run (ordinary and correction) MUST store three texts: header, footer
  and Vår referanse.
- **FR-002**: When a run is generated, the texts MUST be set to the defaults: header «Bruk av Altinn
  » followed by the run's period in Norwegian (e.g. «januar 2027»); footer «Se samarbeid.digdir.no
  for priser og innsyn i egen statistikk»; Vår referanse `Servicedesk@altinn.no` (Økonomi,
  2026-11-04).
- **FR-003**: The LG04 export MUST write the run's stored header at the header field (offset 2509),
  footer at the footer field (2629), and Vår referanse in both responsible fields (3264 and 3289)
  of every line of every order. The configured responsible persons and per-invoice link MUST no
  longer be used.
- **FR-004**: A FORVALTER MUST be able to change any of the three texts on the run's Oversikt page
  while the run is generated and not yet approved.
- **FR-005**: A change MUST be refused, with nothing stored, when a text after trimming is empty,
  is longer than its LG04 field (header 120, footer 120, Vår referanse 25), contains a line break,
  tab or other control character, or contains a character Windows-1252 cannot represent. The
  message MUST name the field and the reason.
- **FR-006**: The page MUST show each field's LG04 limit, and for header and footer regnskap's
  suggested printed-invoice limit (90 and 65) marked as unconfirmed (OQ-23). A text above the
  suggested limit MUST be accepted with a warning, not refused, until FEL confirms which limit
  applies.
- **FR-007**: A change MUST be refused when the run is approved, exported or discarded.
- **FR-008**: Every stored change MUST be recorded in the run's history with the user, the time,
  the period, and for each changed field its old and new text. A save without changes records
  nothing.
- **FR-009**: Runs awaiting approval or export when the feature is installed MUST get the default
  texts; exported and discarded runs MUST keep no texts and show that they were not stored.
- **FR-010**: The LG04 golden file (FEL's `test_3`) MUST still be reproduced byte for byte.
- **FR-011**: docs/07 OQ-23 and the user guide MUST describe the texts, their defaults, limits and
  the lock.

### Key Entities

- **Invoicing run (fakturakjøring)**: gains header text, footer text and Vår referanse. Set at
  generation, editable only while generated, frozen from approval on.
- **History entry (hendelseslogg)**: an «ENDRET» entry on the run listing each changed text with
  old and new value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A run exported without edits carries the three default texts on 100 % of its LG04
  lines.
- **SC-002**: An edited text appears unchanged on 100 % of the run's LG04 lines, and exporting the
  same run again writes byte-identical text fields.
- **SC-003**: No text longer than its LG04 field, or with characters the file cannot hold, can be
  stored — 0 such texts reach a file.
- **SC-004**: 0 text changes are possible after approval.
- **SC-005**: Every text change can be traced to a person and time from the run's history.
- **SC-006**: FEL's sample file is still reproduced byte for byte.

## Assumptions

- All three texts are required; a run cannot have an empty header, footer or Vår referanse. Easy to
  relax if Økonomi wants a blank footer.
- Editing uses the existing role split: FORVALTER changes run data, GODKJENNER approves. No new
  role.
- The defaults are Økonomi's confirmed values and live in the application, not in per-environment
  configuration.
- The printed-invoice limit (90/65) is advisory until FEL confirms it (OQ-23). The printed limit for
  Vår referanse is unknown; the LG04 limit of 25 applies.
- The detail PDFs forsystem archives with the export are not changed by this feature.
