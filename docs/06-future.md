# 06 — Beyond MVP: roadmap and prepared seams

Each item lists *what will come*, *when it becomes relevant*, and *how the MVP already
prepares for it*. Build the seams now, the features later.

## 1. Datavarehus direct integration
- **What**: read the billable data basis (fakturerbart datagrunnlag) directly instead of
  CSV upload. The warehouse is the authoritative source for bruksvolum, fakturaperiode and
  Azure costs from 2027-01-01.
- **How (known since 2026-07-08, the DWH contact)**: the DWH (**finopsdevsa**, PostgreSQL) exposes
  materialized views behind a Data API Builder endpoint —
  `https://finops-dab-api.…azurecontainerapps.io/api/mv_altinn_usage_monthly`. The adapter
  is therefore a REST client mapping that view's rows into the existing staging pipeline.
  Blocked on: the API security layer (FEL↔platform, paused for vacation) and the view's
  column contract, which must be aligned with our produkt codes and `docs/csv-format.md`.
- **When**: realistic before the December dry-run if the security discussion resumes in
  August. CSV upload stays as fallback either way. Data timing: the previous month is
  complete on the **6th** (Azure costs final 5 days in) — any scheduler must respect this.
- **Prepared by**: `UsageDataSource` port; `bruksdata.kilde` = 'DWH'; identical staging
  and validation pipeline regardless of source.

## 2. Dynamics 365 CRM as customer source
- **What**: customer/agreement data read from CRM instead of the local registry.
- **How (known since 2026-08-18)**: CRM *is* the agreed short-term solution, so this moved
  from "maybe later" to "the expected path". The fields already exist in the CRM SANDKASSE
  environment on an **`Integrasjon`** row with `Integrasjonstype = Altinn`, linked to a
  `Virksomhet`. Caveat: we have **no CRM access** — this mapping is read off a single
  screenshot and must be verified against real field names/metadata before any import code:
  organisasjonsnummer → `kunde.organisasjonsnummer`, fakturaadresse/fakturamottaker →
  `kunde.fakturamottaker_orgnr` (careful: not always an orgnr, OQ-7), referanse →
  `kunde_referanse.fakturareferanse`, bestillingsnummer →
  `kunde_referanse.bestillingsnummer`, kundenummer i Unit4 → `kundenummer_regel.kundenummer`,
  avtalestatus → `kunde.avtalestatus` (value shown as `Active`, so values need mapping, not
  copying). Fields with no counterpart in our model: `Kundetype Altinn`,
  `Bruksvilkår Altinn - Dato`/`- Signatar`, `Beskrivelse`.
  Two shape gaps to raise when the interface is designed: CRM has **one referanse per Altinn
  avtale** while FinMod rule 2 needs it **per product**, and **one kundenummer field** while
  rule 3 allows several per orgnr (`servicekode`/`tilleggstekst` have no CRM counterpart).
  Our model is the richer one — the import must either flatten or CRM must grow.
- **Blocked on**: the interface itself (API vs export) is unmapped and undated (OQ-5).
- **When**: unknown; the December dry-run must therefore be assumed to run on the registry.
- **Prepared by**: `CustomerSource` port; `kunde.kilde` column; snapshots on faktura mean a
  source switch never rewrites history. Expect a sync/import mode first (CRM → local
  tables) rather than live reads — the registry tables then become a cache with
  `kilde='CRM'` rows read-only in the UI.

## 3. Automated delivery to DFØ + receipt handling
- **What**: replace manual download-and-send with SFTP/API delivery; ingest kvittering /
  error feedback into run status (new statuses e.g. LEVERT, IMPORT_BEKREFTET, IMPORT_FEILET).
- **When**: after the FEL contact documents today's channel and DFØ's options (OQ-8); value grows
  once volume/frequency grows.
- **Prepared by**: exports archived with sha256 in `eksportfil`; the state machine is data,
  adding states is a migration; delivery becomes a new adapter alongside `FileArchive`.

## 4. Scheduling
- **What**: cron-triggered generation (e.g. Container Apps Job / k8s CronJob calling an API
  endpoint or CLI runner) + `Notifier` telling the approver a run awaits.
- **When**: after a few smooth manual months.
- **Prepared by**: generation is a service method with explicit period parameter — a
  scheduler only supplies the parameter; approval stays human.

## 5. Avregning (settlement/true-up)
- **What**: corrections, crediting, retroactive price effects. Mechanism undefined —
  owned by Økonomi + VIS (OQ-6). The 2026-08-18 løsningsbeskrivelse lists two questions:
  how avregning fits the technical solution, and how the avregningsgrunnlag is handled in
  the invoicing process.
- **When**: unknown; possibly during 2027.
- **Prepared by**: full traceability chain (line → bruksdata + pris) makes recomputation
  diffable; negative-amount lines and a `KREDIT` line/run flavor are the likely extension —
  do not build until defined.

## 6. Onboarding the old services (dp, e-signering, Altinn current, BOD)
- **What**: if/when the older services move onto this system, they become products with
  their own kontering and (quarterly/yearly) cadence.
- **When**: no plan today — old system continues under FEL. Revisit after MVP proves itself.
- **Prepared by**: kontering per product; period type is monthly today but isolated in
  `Periode` (extending to quarter/year is a value-type change plus engine grouping, not a
  schema rewrite).

## 7. Authoritative product catalog
- **What**: FinMod wants a real product master eventually; forsystem's product table is
  explicitly *temporary* master.
- **Prepared by**: products referenced by id everywhere; a future sync flips the UI to
  read-only, same pattern as CRM.

## 8. PDF detail views served to tjenesteeiere
- **What**: today PDFs are downloaded and distributed manually; the FEL contact/FEL handles the
  hosted detail-view line of work. A future integration could push our PDFs (or the data
  behind them) to that platform.
- **Prepared by**: `faktura_uuid` filename convention matches today's
  `<kundenummer>-<uuid>.pdf` pattern; PDFs are archived artifacts, re-servable anytime.

## 9. Nice-to-haves once data accumulates
Period-over-period comparison views (the ±30 % control's data is already there), export of
kontrollfunn history, Grafana-style run dashboards from `fakturakjoring`/`hendelseslogg`.
