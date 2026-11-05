# Feature Specification: Acknowledge control findings and export the Kontroller lists

**Feature Branch**: `claude/013-kvittering-funn-fac4c8`

**Created**: 2026-11-05

**Status**: Draft — ready for planning

**Input**: Økonomi's kickoff list, two rows.
Row 34: «Ønsker at man skal kunne kvittere ut at et kontrollfunn er akseptert. Videre at det fremkommer på
oversikten når funnene er sjekket ut. Det skal ikke være mulig å godkjenne kjøring før man har sjekket av
alle funnene.» Action items: a checkbox «Funn akseptert» in the findings list; an accepted finding no
longer counts on the Kontroller tab; the Oversikt says something like «1 advarsel, gjennomgått og sjekket
ut. Hindrer ikke godkjenning»; approval is impossible until every finding is acknowledged.
Row 35: «Mulighet for å eksportere de ulike listene under Kontroller til relevante formater (PDF, CSV,
excel, bilde). Gjerne inne på Kontroller siden, og ikke på egen side med Filer.»

## Context

Generation records control findings (`kontrollfunn`) with a severity from `Kontrollkode`. BLOKKERENDE
findings stop approval; they are fixed in the registry and the run is discarded and generated again.
ADVARSEL findings are shown but do not stop approval today, so nothing records that anyone looked at
them. The Kontroller tab groups findings per code (specs/005), and its tab badge counts blocking
findings, else warnings. Two lists already download as XLSX/CSV through `/kjoringer/{id}/{liste}.{format}`,
which logs every download (`LASTET_NED`): the under-threshold list (specs/011, Fakturaer tab) and
«Fakturaer uten fakturareferanse» (specs/012, Kontroller tab).

## Clarifications

### Session 2026-11-05 (design decisions settled in this spec)

- Q: Can a BLOKKERENDE finding be acknowledged? → A: **No.** Only ADVARSEL findings can be accepted.
  A blocking finding is fixed by correcting the data and regenerating; acknowledging it would let a run
  through that the controls exist to stop. The service refuses it and the database forbids it.
- Q: Who may acknowledge? → A: **FORVALTER and GODKJENNER**, not LESER. The FORVALTER generated the run
  and often investigates the warnings; the GODKJENNER (Økonomi) reviews them before approving. Approval
  stays the GODKJENNER's own, audited act (principle VII), and the Kontroller tab shows who accepted each
  finding, so the GODKJENNER can see who vouched for what.
- Q: Per finding or per group? → A: **Per finding** is the unit stored, audited and checked by the
  approval gate, because each finding is about one customer or invoice (a 45 % deviation for customer A
  is a different judgment from one for customer B). The group row's checkbox is a shortcut that accepts
  (or un-accepts) every warning in the group in one audited action — needed because one code can have
  dozens of findings (e.g. `AKTIV_KUNDE_UTEN_BRUK`). The expanded list has a checkbox per finding.
- Q: When can acknowledgements change? → A: Only while the run is GENERERT. From approval on they are part
  of what was approved and are frozen, like the invoice texts (specs/009).
- Q: Regeneration? → A: A regenerated run is a new run with new findings, so it starts with nothing
  accepted. Acknowledgements never carry over.
- Q: Correction runs (specs/007)? → A: Same rules.
- Q: Which lists are exported (row 35)? → A: Every table on the Kontroller tab: Kontrollfunn, Avstemming
  bruksdata (per product and type), Ikke fakturert, and Fakturaer uten fakturareferanse (already there).
  The LG04-avstemming box is two numbers, not a list, and is not exported.
- Q: PDF and image (row 35)? → A: Out of scope. The page already prints cleanly (print stylesheet hides
  navigation and buttons), so the browser's «Skriv ut → Lagre som PDF» gives a PDF of the tab today, and a
  screenshot gives an image. A server-side PDF of working lists would add a second rendering path to
  maintain for no data the XLSX lacks; XLSX is what Økonomi sorts and filters.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Accept a warning, and see that it is accepted (Priority: P1)

Before approving January 2027, Økonomi opens the Kontroller tab. The run has two `AKTIV_KUNDE_UTEN_BRUK`
warnings and one `STORT_AVVIK`. Økonomi checks each customer, ticks «Funn akseptert» on each finding,
and the tab badge goes from 3 to nothing. The Oversikt now says «3 advarsler, gjennomgått og sjekket ut.
Hindrer ikke godkjenning», and the Historikk tab shows who accepted what, and when.

**Why this priority**: Økonomi wants proof that every warning was looked at before money is invoiced.

**Independent Test**: Generate a run with one warning; accept it as FORVALTER; the badge disappears, the
finding shows «Akseptert av … dd.mm.åååå tt:mm», the Oversikt shows the «sjekket ut» line, and the
run's Historikk has an `AKSEPTERTE_FUNN` row.

**Acceptance Scenarios**:

1. **Given** a GENERERT run with an ADVARSEL finding, **When** a FORVALTER or GODKJENNER ticks «Funn
   akseptert», **Then** the finding records who and when, and the run's history logs `AKSEPTERTE_FUNN`
   with the code, the finding ids and the count.
2. **Given** an accepted finding, **When** the checkbox is unticked, **Then** the acceptance is removed
   and the history logs `ANGRET_AKSEPT`.
3. **Given** a group of 30 warnings of one code, **When** the group's checkbox is ticked, **Then** all 30
   are accepted in one action and one history row; unticking it removes all 30.
4. **Given** some but not all of a group are accepted, **Then** the group row says «N av M akseptert» and
   its checkbox is unticked; ticking it accepts the rest.
5. **Given** an accepted warning, **Then** the Kontroller tab badge and the run list do not count it.
6. **Given** a BLOKKERENDE finding, **Then** it has no checkbox, the row says it is fixed by regenerating,
   and a request to accept it is refused.
7. **Given** a LESER, **When** they try to accept, **Then** they are refused (403) and nothing changes.
8. **Given** a GODKJENT, EKSPORTERT or FORKASTET run, **Then** the checkboxes are disabled and a request
   to change them is refused with a message.

---

### User Story 2 - Approval waits for every warning (Priority: P1)

A GODKJENNER opens a run with no blocking findings but one warning not yet accepted. «Godkjenn» is
disabled, and the checklist says the warning must be checked out on the Kontroller tab first. Once it is
accepted, «Godkjenn» is enabled.

**Why this priority**: Økonomi's explicit rule — «Det skal ikke være mulig å godkjenne kjøring før man har
sjekket av alle funnene».

**Independent Test**: Approving a run with an unaccepted warning is refused by the service, not only by a
disabled button; after accepting it, approval succeeds.

**Acceptance Scenarios**:

1. **Given** an unaccepted ADVARSEL, **When** approval is requested (button or POST), **Then** it is
   refused with «N advarsler er ikke sjekket ut …» and the run stays GENERERT.
2. **Given** every ADVARSEL accepted and no BLOKKERENDE, **Then** approval succeeds.
3. **Given** a BLOKKERENDE finding, **Then** approval stays refused whatever is accepted.
4. **Given** a run with no findings, **Then** approval works as before.
5. **Given** the Oversikt of a GENERERT run, **Then** the checklist shows open warnings as a step to do
   («N advarsler er ikke sjekket ut …») and accepted ones as done («N advarsler, gjennomgått og sjekket
   ut. Hindrer ikke godkjenning»), with correct singular/plural.
6. **Given** a run discarded and generated again, **Then** the new run's warnings are all unaccepted.
7. **Given** a correction run with a warning, **Then** the same rules apply.

---

### User Story 3 - Download the Kontroller lists (Priority: P2)

Økonomi wants to sort and filter the findings and the usage reconciliation in Excel and file them with
the month's documentation. Each list on the Kontroller tab has «Last ned (XLSX)» and «CSV» next to it.

**Why this priority**: Row 35; useful, but approval does not depend on it.

**Independent Test**: Download each list as XLSX and CSV; the rows match the tab, and each download is
in the run's history as `LASTET_NED` with the list name.

**Acceptance Scenarios**:

1. **Given** the Kontrollfunn list, **When** downloaded, **Then** each finding is one row with periode,
   alvorlighet, kode, beskrivelse, ordre, kundenummer, kundenavn, funn_akseptert (ja/nei; empty for
   blokkerende), akseptert_av and akseptert_tidspunkt.
2. **Given** Avstemming bruksdata, **When** downloaded, **Then** each product × type row has the same
   columns as the table, numbers as numbers in XLSX.
3. **Given** Ikke fakturert, **When** downloaded, **Then** each row has orgnr, product, type, antall,
   beløp, kode and årsak.
4. **Given** a correction run (no usage reconciliation), **Then** the two reconciliation downloads are
   not offered, and a direct request is refused with a message.
5. **Given** an empty list, **Then** no download is offered for it.
6. **Given** any download, **Then** it is logged as `LASTET_NED` with list, format and status, and the
   file name carries period, version (corrections) and status.

### Edge Cases

- Runs approved or exported before this feature have unaccepted warnings. They are past the gate; their
  Kontroller tab shows the warnings as not accepted, with disabled checkboxes.
- A GENERERT run that exists when this feature is deployed needs its warnings accepted before approval.
- Two users toggling the same finding: the run row is locked for each change, as for approval, so a
  change and an approval never interleave; the last change wins and both are logged.
- A request naming a finding of another run is refused like an unknown finding.
- A request that changes nothing (already accepted) writes no history row.
- Printing the Kontroller tab hides forms, so the accepted state is also shown as text («Akseptert av …»).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A FORVALTER or GODKJENNER MUST be able to mark an ADVARSEL finding of a GENERERT run as
  accepted, and remove the mark; the finding stores who and when.
- **FR-002**: BLOKKERENDE findings MUST NOT be acceptable (service and database).
- **FR-003**: Accepting and un-accepting MUST be possible per finding, and for all warnings of one code
  in a run in one action.
- **FR-004**: Every change MUST be logged in the run's history (`AKSEPTERTE_FUNN` / `ANGRET_AKSEPT`) with
  user, time, code, finding ids and count; a request that changes nothing logs nothing.
- **FR-005**: Approval MUST be refused while the run has a BLOKKERENDE finding or an unaccepted ADVARSEL
  finding; the Oversikt MUST disable «Godkjenn» and say why.
- **FR-006**: The Kontroller tab badge and the run list MUST count only blocking findings and unaccepted
  warnings.
- **FR-007**: The Oversikt checklist MUST show unaccepted warnings as open and accepted ones as «N
  advarsel/advarsler, gjennomgått og sjekket ut. Hindrer ikke godkjenning».
- **FR-008**: Acknowledgements MUST be frozen once the run leaves GENERERT, and a regenerated run MUST
  start with none.
- **FR-009**: The Kontroller tab's explanation of severities MUST say that warnings must be accepted
  before approval.
- **FR-010**: The Kontroller tab MUST offer XLSX and CSV downloads of Kontrollfunn, Avstemming bruksdata
  and Ikke fakturert, next to each list, through the existing list endpoint, each download logged.
- **FR-011**: The LG04 golden file MUST stay byte-identical.
- **FR-012**: The user guide, the feature list, docs/09 and docs/03 MUST describe the behaviour.

### Key Entities

- **Kontrollfunn**: gains `akseptert_av` and `akseptert_at` (both null, or both set; only for ADVARSEL).
- **Hendelseslogg**: new actions `AKSEPTERTE_FUNN` and `ANGRET_AKSEPT` on the run.

## Success Criteria *(mandatory)*

- **SC-001**: 0 runs can be approved with an unaccepted warning or any blocking finding.
- **SC-002**: For 100 % of approved runs, every warning has a named person and time who accepted it.
- **SC-003**: Every table on the Kontroller tab can be opened in Excel in one click.

## Assumptions

- «Alle funnene» means all warnings; blocking findings cannot be checked out, only fixed.
- «Forsvinner 1-tallet på Kontroller» means the badge counts only what still needs attention.
- No comment field on acceptance: Økonomi asked for a checkbox. The history records who and when; a
  reason can be added later if Økonomi asks.
- The run list keeps showing accepted warnings as a quiet note, so a run with only accepted warnings is
  distinguishable from a run with none.
