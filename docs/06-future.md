# 06 — Beyond MVP: roadmap and prepared seams

Each item lists *what will come*, *when it becomes relevant*, and *how the MVP already
prepares for it*. Build the seams now, the features later.

## 1. Datavarehus direct integration
- **Status (2026-10)**: **built** — specs/001-dwh-usage-import. A FORVALTER can "Hent fra
  datavarehus" for a period; the DAB view `mv_altinn_usage_monthly` (daily rows) is aggregated to
  monthly `bruksdata` via the `produkt_kildenavn` mapping and runs through the same preview/commit as
  CSV. The raw response is archived with SHA-256. An optional scheduler (`forsystem.dwh.planlegging`)
  stages the previous month at 06:00 on the 7th for a FORVALTER to confirm. Off by default in code
  (`forsystem.dwh.enabled`); switched on in tt02 and prod on 2026-10-07 for testing (scheduler off).
- **Remaining before production use**: the product-name mapping must be confirmed (OQ-18); the DWH
  team must confirm Entra auth for forsystem's identity (the API has required a token since
  2026-10-12; scope and access are unconfirmed) and fix the entity key that makes paging lossy
  (OQ-19 — until then forsystem fetches a month in one request and refuses any `nextLink`); view retention
  and refresh semantics (OQ-20); quantity units (OQ-21). The issues are written up for the DWH team
  in Norwegian in specs/001-dwh-usage-import/sak-til-dwh-teamet.md.
- **Data timing**: the previous month is complete on the **6th** (Azure costs final 5 days in).
- CSV upload stays as the fallback.

## 2. Dynamics 365 CRM as customer source — settled: through the DWH
- **Decided 2026-11-24** (Økonomi, docs/09 B-24, specs/018): CRM is the only customer master and
  reaches forsystem **through the datavarehus** («kunderegister = DWH = integrasjon mot CRM»). The
  DWH export carries organisation name, kundenummer, fakturareferanse and bestillingsnummer per
  orgnr; forsystem stores them per import (`bruksdata_kunde`) and uses them at generation. The local
  customer registry is removed (V18). **A direct CRM import is no longer planned**, and the
  `CustomerSource` seam is gone with the registry.
- **What remains**: CRM data the DWH does not deliver — product-specific fakturareferanser, several
  kundenummer per orgnr (`servicekode`/`tilleggstekst`), an avvikende fakturamottaker — is not
  supported. If Økonomi needs it, CRM/DWH must deliver it in the export (OQ-28); forsystem would then
  read more columns, not a new source. Avtalestatus is checked by the DWH, not forsystem (OQ-13).
- **Background** (2026-08-18): the fields exist in CRM on an **`Integrasjon`** row with
  `Integrasjonstype = Altinn`, linked to a `Virksomhet`: organisasjonsnummer,
  fakturaadresse/fakturamottaker (not always an orgnr, OQ-7), referanse, bestillingsnummer,
  kundenummer i Unit4, avtalestatus. CRM has one referanse per avtale and one kundenummer field —
  the same shape forsystem now uses.

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
  read-only.

## 8. PDF detail views served to tjenesteeiere
- **What**: today PDFs are downloaded and distributed manually; the FEL contact/FEL handles the
  hosted detail-view line of work. A future integration could push our PDFs (or the data
  behind them) to that platform.
- **Prepared by**: `faktura_uuid` filename convention matches today's
  `<kundenummer>-<uuid>.pdf` pattern; PDFs are archived artifacts, re-servable anytime.

## 9. Nice-to-haves once data accumulates
Period-over-period comparison views (the ±30 % control's data is already there), export of
kontrollfunn history, Grafana-style run dashboards from `fakturakjoring`/`hendelseslogg`.
