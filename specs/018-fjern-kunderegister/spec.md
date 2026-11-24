# Feature Specification: Remove the customer registry — customer data comes from the DWH

**Feature Branch**: `018-fjern-kunderegister`

**Created**: 2026-11-24

**Status**: Draft — ready for planning

**Input**: Ticket from Økonomi: «Det skal ikke være mulig å opprette kunder direkte fra forsystemet.
Videre skal det ikke være avvik mellom kunderegister og DWH, da kunderegister = DWH (=integrasjon mot
CRM). Fjerne kunderegister fra forsystem.»

## Context

Today forsystem keeps its own customer registry (kunde, fakturareferanser, kundenummer rules) and
uses it as the master when it generates invoices. The DWH export already carries the customer
fields per organisation (name, kundenummer, fakturareferanse, bestillingsnummer), taken from CRM.
The two can therefore disagree, and the system shows this on the Bruksdata preview in two ways:

- A notice «N organisasjoner i fila finnes ikke i kunderegisteret» with a button **«Opprett N kunder
  fra fila»** that creates customers (status AKTIV) from the file.
- A list of **Advarsler** where the DWH's fakturareferanse or kundenummer differs from the
  registry's (for example «datavarehuset har fakturareferanse «Altinn», kunderegisteret har
  «Lene Brandsø Reiersen»»).

Økonomi's decision: CRM is the only customer master. It reaches forsystem through the DWH, so the
customer data forsystem uses is the data in the imported usage. A separate registry that can be
edited, and that can deviate, must not exist. This also settles the open part of OQ-5 (the CRM →
forsystem interface): the interface is the DWH.

The list page and the «Ny kunde» form were already removed (commit `70273ab`). This feature removes
the rest: the create-from-file button, the deviation warnings, the customer detail page reached by
direct URL, and the registry as the input to invoice generation.

## Clarifications

### Session 2026-11-24

- Q: The registry holds data the DWH does not deliver: product-specific fakturareferanser, several
  kundenummer per orgnr (with servicekode/tilleggstekst), and an avvikende fakturamottaker. What
  happens to them? → A: They are dropped. An invoice uses the DWH's one kundenummer and one
  fakturareferanse per organisation, and the fakturamottaker is the organisation itself. If Økonomi
  needs any of them back, they must come through CRM/DWH (new open question OQ-28).
- Q: What replaces the controls that depend on the registry? → A: MANGLER_KUNDE goes. The existing
  blocking control MANGLER_KUNDENUMMER now fires when the DWH delivers no kundenummer for an
  organisation with billable usage. INAKTIV_AVTALE and AKTIV_KUNDE_UTEN_BRUK go: the DWH's flag
  already filters out usage without an active agreement (OQ-4, OQ-25), and the DWH delivers only
  organisations with usage.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Import usage without customer maintenance (Priority: P1)

A FORVALTER uploads a DWH export (or fetches it from the API) and previews it. The preview shows
the usage and the import's own findings (rejected rows, flags, months outside the period). It says
nothing about a customer registry: no «finnes ikke i kunderegisteret» notice, no button to create
customers, and no warnings that the DWH's fakturareferanse or kundenummer differ from forsystem's.

**Why this priority**: This is the ticket as written. It removes the action Økonomi does not want
(creating customers in forsystem) and the warnings that should not be possible.

**Independent Test**: Upload the fixture export that today gives «Opprett 7 kunder fra fila» and the
deviation warnings. Confirm the preview shows neither, and that confirming the import works.

**Acceptance Scenarios**:

1. **Given** an export with organisations forsystem has never seen, **When** a FORVALTER previews
   it, **Then** no notice about missing customers and no create-customers button appear.
2. **Given** an export whose fakturareferanse for an organisation differs from what an earlier
   import carried, **When** it is previewed, **Then** no deviation warning about a customer registry
   is shown.
3. **Given** any user, **When** they send a request to the old create-customers address or open a
   customer detail address, **Then** the page does not exist (not found), and no customer is created
   or changed.

---

### User Story 2 — Invoices take customer data from the imported usage (Priority: P1)

When a run is generated for a period, each invoice gets its customer name, kundenummer,
fakturareferanse and bestillingsnummer from the DWH data imported for that period and
organisation. The LG04 file, PDF and spreadsheets show the same values the DWH delivered.

**Why this priority**: Without it, removing the registry leaves the generation with no customer
data. Stories 1 and 2 must ship together.

**Independent Test**: Import an export for one period where organisation A has kundenummer `K-A`
and fakturareferanse `REF-A`, with no customer stored anywhere else. Generate the run and check
that A's invoice and its LG04 lines carry `K-A` and `REF-A`.

**Acceptance Scenarios**:

1. **Given** imported usage for organisation A with kundenummer and fakturareferanse, **When** the
   run is generated, **Then** A gets one invoice with exactly those values and the name from the
   import.
2. **Given** an empty bestillingsnummer from the DWH, **When** the run is generated, **Then** the
   existing fallback applies (bestillingsnummer = fakturareferanse, specs/012).
3. **Given** a later import that changes A's fakturareferanse, **When** the run is regenerated,
   **Then** the new value is used. An already approved run keeps the values it was approved with.
4. **Given** usage for an organisation with no kundenummer in the DWH, **When** the run is
   generated, **Then** the blocking finding MANGLER_KUNDENUMMER names the organisation, and the run
   cannot be approved until a corrected import arrives.
5. **Given** usage for an organisation with no fakturareferanse, **When** the run is generated,
   **Then** the warning MANGLER_FAKTURAREFERANSE appears as today (specs/012).
6. **Given** the rows imported for one organisation and period disagree on kundenummer or
   fakturareferanse, **When** the run is generated, **Then** a blocking finding names the
   organisation and the conflicting values, and forsystem does not pick one of them.

---

### User Story 3 — Help, docs and controls describe the new reality (Priority: P2)

The learning center, control texts and docs no longer tell users to fix customer data in
forsystem. Where a customer value is wrong or missing, they tell the user to correct it in CRM
and import again.

**Why this priority**: The constitution requires the help articles to change in the same pull
request. Wrong guidance would send users to a page that no longer exists.

**Independent Test**: The build's link and control-code checks pass. No article links to a removed
page. Every remaining control code has a «Slik løser du det» text that points to CRM.

**Acceptance Scenarios**:

1. **Given** the learning center, **When** a user browses it, **Then** there is no «Kunder» section,
   and no articles on creating customers, adding fakturareferanse, adding kundenummer rules or
   avvikende fakturamottaker.
2. **Given** a MANGLER_KUNDENUMMER or MANGLER_FAKTURAREFERANSE finding, **When** the user opens its
   help, **Then** it says the value is maintained in CRM, reaches forsystem through the DWH, and
   that a new import is needed after the correction.

### Edge Cases

- **Runs generated before this change**: their invoices keep the customer values they were generated
  with. Opening, exporting and showing history for them must still work without the registry.
- **Old-format exports without customer columns**: an organisation then has no kundenummer and gets
  MANGLER_KUNDENUMMER. The import itself is not refused.
- **Several imports for the same period** (re-upload, API fetch after a file): the customer values
  come from the import the run's usage comes from. The rule must be the same one that decides which
  usage counts.
- **Organisation name**: the flagged export delivers the name in lower case. It is shown as
  delivered. Prettifying a name would be inventing a value.
- **Several kundenummer per organisation** (e.g. Utdanningsdirektoratet): no longer supported. The
  organisation gets the one kundenummer the DWH delivers (OQ-28).
- **Threshold (terskelverdi, specs/011) and the period comparison (STORT_AVVIK, NY_KUNDE)**: they
  are computed per organisation instead of per registered customer. The results must not change for
  the same input.
- **Event log**: earlier entries about customers, references and rules stay readable. No new ones
  can be made.

## Requirements *(mandatory)*

### Functional Requirements

**Removing the registry**

- **FR-001**: Forsystem MUST NOT offer any way to create, edit or delete customers, fakturareferanser
  or kundenummer rules: no page, form, button, endpoint or import.
- **FR-002**: The DWH import preview MUST NOT show the missing-customers notice, the create-customers
  action, or warnings that compare DWH customer values with forsystem.
- **FR-003**: The controls MANGLER_KUNDE, INAKTIV_AVTALE and AKTIV_KUNDE_UTEN_BRUK MUST be retired.
  Findings stored on earlier runs stay readable with their original text.
- **FR-004**: Registry data MUST NOT be read by invoice generation, exports or pages after this
  change.

**Customer data from the DWH**

- **FR-005**: Each import MUST keep, per organisation, the customer values the DWH delivered:
  organisation name, kundenummer, fakturareferanse and bestillingsnummer. The archived raw file
  stays the proof (constitution III).
- **FR-006**: Generation MUST take an invoice's customer values from the imported data for the run's
  period and organisation. They are frozen on the invoice at generation, as today's snapshot is.
- **FR-007**: An organisation with billable usage and no kundenummer MUST give the blocking finding
  MANGLER_KUNDENUMMER.
- **FR-008**: An organisation whose imported rows for the period disagree on kundenummer or
  fakturareferanse MUST give a blocking finding that lists the values. The system MUST NOT choose
  one.
- **FR-009**: Each organisation MUST get exactly one invoice per run, with the fakturamottaker equal
  to the organisation itself.
- **FR-010**: The bestillingsnummer fallback, the MANGLER_FAKTURAREFERANSE warning and the shared-
  reference control MUST work as today, fed by DWH values.
- **FR-011**: Terskelverdi, STORT_AVVIK and NY_KUNDE MUST group by organisation number. For input
  where each organisation had one customer with one kundenummer, the outcome is unchanged.

**History and traceability**

- **FR-012**: Invoices, findings, exports and event-log entries from runs made before the change MUST
  still open, export and read correctly.
- **FR-013**: The run's pages and exports MUST show a customer's name and orgnr from the invoice's
  own data, not from a live lookup.

**Help and docs**

- **FR-014**: The learning center, control-code help texts and docs (context, data model, decision
  log, feature overview, open questions) MUST describe CRM → DWH as the only source of customer data.
  OQ-5 is marked answered. A new OQ-28 records the dropped capabilities: product-specific references,
  several kundenummer per organisation, and avvikende fakturamottaker.

### Key Entities

- **Kundedata per import**: the customer values the DWH delivered for one organisation in one
  import: orgnr, name, kundenummer, fakturareferanse, bestillingsnummer. Read-only. It replaces the
  customer registry as the input to generation.
- **Faktura** (existing): keeps its customer snapshot (name, kundenummer, fakturareferanse,
  bestillingsnummer, fakturamottaker) and identifies its customer by orgnr instead of a registry
  link.
- **Retired**: Kunde, KundeReferanse, KundenummerRegel as maintained data, and the Avtalestatus
  values. Their history survives in the event log and in invoice snapshots.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero places in the application where a user can create or change customer data.
- **SC-002**: Importing the reference export from the ticket's screenshots shows 0 customer-registry
  notices and 0 deviation warnings in the preview.
- **SC-003**: For a period imported from the DWH, 100 % of invoices carry the same kundenummer and
  fakturareferanse the DWH delivered for that organisation, checked in the LG04 file.
- **SC-004**: All runs generated before the change open, export and show their findings exactly as
  before. The LG04 golden files are byte-identical.
- **SC-005**: A monthly run needs no customer maintenance step in forsystem. The month routine in the
  learning center has no such step.

## Assumptions

- The DWH delivers at most one kundenummer and one fakturareferanse per organisation and period.
  When it does not, that is a data error to fix in CRM (FR-008), not something forsystem resolves.
- The DWH flag (`flag_inv_altinn`) and the DWH's own agreement filtering (OQ-4) are enough to keep
  organisations without an active agreement off the invoice. Forsystem no longer checks agreement
  status itself.
- Customer data in the existing registry is not migrated anywhere. Invoices already generated keep
  their snapshots. The registry's tables may be dropped once nothing reads them; the event log keeps
  their history.
- The demo profile's seed data moves to demo DWH usage that carries customer values, so the
  December 2026 dry-run still works locally.
- The roles stay the same (LESER, FORVALTER, GODKJENNER). Only the help table's customer row goes.
- Out of scope: a direct CRM integration (docs/06 §2), BRREG lookups (they validated manual
  registration only, so they go with it), and any new way to override DWH values.
