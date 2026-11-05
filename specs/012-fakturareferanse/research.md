# Research: Fakturareferanse control and bestillingsnummer fallback

## R1 — Where the fallback happens: at generation only

- **Decision**: In `FakturakjoringService.bygg`, when the line's reference pair is snapshotted
  (`Referansepar.av`). The registry (`kunde_referanse`) is not changed, and nothing changes in the DWH
  import or the customer form.
- **Rationale**: One place covers every source — manual registry entries, customers created from a DWH
  export, and the future CRM import. CRM is the master for customer data (B-14); copying the value into
  the registry would make the registry differ from CRM, and a later fix of the fakturareferanse would
  leave a stale copied bestillingsnummer behind. At generation, a regenerated run always uses the
  current fakturareferanse. Lines are snapshots, so the LG04, PDFs and spreadsheets agree.
- **Alternatives**: At import/creation (registry diverges from source, stale copies); both (two rules to
  keep in step). Rejected.
- **Visibility**: The customer page shows «— (fakturareferansen brukes)» for an empty bestillingsnummer.

## R2 — Interaction with `DELT_REFERANSE`

- **Decision**: Apply the fallback per line before invoices are grouped and before the split check.
- **Rationale**: The export splits by the pair (fakturareferanse, bestillingsnummer). The fallback maps
  (r, ∅) → (r, r) and leaves every other pair alone, so it is a function on the keys: distinct keys can
  only merge, never multiply. It removes the one split that existed only because one row left the
  bestillingsnummer empty and another wrote it out. Tested both ways.

## R3 — What counts as «mangler»

- **Decision**: A line whose snapshotted fakturareferanse is empty. A product row with only a
  bestillingsnummer replaces the customer default as a whole (the existing `velgReferanse` rule), so its
  product's lines count as missing.
- **Rationale**: The control reports what actually goes to Unit4. Field-wise inheritance from the
  default would change which references existing customers get, which is Økonomi's call →
  `TODO(OQ-24)`.

## R4 — Severity and granularity

- **Decision**: ADVARSEL, one per invoice (`faktura_id` set), message with customer, orgnr, kundenummer
  and product names.
- **Rationale**: The kickoff row says «tilsvarende Aktiv_kunde_uten_bruk», which is an ADVARSEL. OQ-15
  had deferred the control until the mandatory-field list was final; Økonomi has now asked for it, and
  a warning cannot stop a run wrongly. Per invoice because the reference sits on the invoice and a
  customer can have several kundenumre; the Kontroller tab shows findings as text, so the text names the
  customer.

## R5 — Where the list lives

- **Decision**: A section «Fakturaer uten fakturareferanse» on the run's Kontroller tab, under the
  findings, with XLSX/CSV downloads via the existing list endpoint
  (`/kjoringer/{id}/uten-fakturareferanse.{csv|xlsx}`). Computed from the run's lines; no new table.
- **Rationale**: The kickoff row is a «Kjøringer, kontroller» row; another kickoff row asks for lists
  under Kontroller to be exportable; and another proposes removing the customer list from the UI
  (CRM is master), so the Kunder page is the wrong home. Reading the lines makes the list right for
  runs generated before this feature too.
- **Open**: a registry-wide list of all active customers without a reference → `TODO(OQ-24)`.

## R6 — Correction runs

- **Decision**: Same fallback and warning. Their invoices also go to Unit4 and are built by the same
  `bygg`.
