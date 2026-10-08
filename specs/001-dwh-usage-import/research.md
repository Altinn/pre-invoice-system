# Research: Usage import from the datavarehus API

Probed 2026-10-06 against
`https://finops-dab-api.wonderfulriver-01d57917.norwayeast.azurecontainerapps.io/api/mv_altinn_usage_monthly`.

## What the API is

- **Data API Builder (DAB) 1.4.35** (Microsoft's OData-style REST/GraphQL layer) in Azure Container
  Apps, in front of the **finopsdevsa** PostgreSQL DWH (OQ-4). REST only; `/graphql` is 404.
  OpenAPI at `/api/openapi`.
- Entities exposed: `mv_altinn_usage_monthly` (our target), `v_altinn_usage_daily_90d_innsikt_te`,
  `v_altinn_usage_monthly_12m_innsikt_te` (insight views, no customer refs), `v_correspondence_*`,
  `v_email_hourly`, `v_sms_hourly`.
- Supports `$first`, `$after` (cursor), `$filter`, `$orderby`, `$select`. Default page 100.
- **No authentication.** Anonymous `GET` returns organisation numbers, names, kundenummer and
  fakturareferanse.

## The model behind `mv_altinn_usage_monthly`

| Column | Type (OpenAPI) | Observed |
|---|---|---|
| `organisation_number` | string | 9 digits; 57 distinct. **Configured as the entity's only key.** |
| `organisation_name` | string | lower-case short name (e.g. `finanstilsynet`) |
| `product_name` | string | display names: `Autorisasjon`, `Altinn studio`, `Varsling e-post`, `Formidling` |
| `quantity` | number | integers 1 … 11 213 261 |
| `amount_nok` | number | always `null` |
| `transaction_date` | string (DateTimeOffset in filters) | **daily**, `YYYY-MM-DD` |
| `source_table` | string | 1:1 with product (`digdir_prod_autorisasjon_data_main`, `…_studio_instanser_…`, `…_core_email_…`, `…_broker_data`) |
| `fakturareferanse` | string | 30/2519 null; one free-text value `timefakturering: 1100eol` |
| `kundenummer` | string | 4 chars, one per orgnr |
| `bestillingsnummer` | string | always `null` |

Snapshot facts: 2 519 rows, all dated 2026-09-01…30; one kundenummer/fakturareferanse per orgnr;
unique on (orgnr, product_name, transaction_date). Despite the name, the grain is **one row per
organisation × product × day** and it currently holds **only the last complete month** (no August,
no October). The 12-month insight view also shows product names not yet in the billing view:
`Melding`, `Varsling SMS (Norge)`, `Varsling SMS (utland)`, `altinn-studio-applikasjonsinfrastruktur`.

The view also joins in customer references (`kundenummer`, `fakturareferanse`, `bestillingsnummer`)
— i.e. the DWH already reads CRM/registry data. That overlaps forsystem's own registry (OQ-5, OQ-10).

## Defects found (must be raised with the DWH/platform team)

- **R1 – Public data.** No auth on an endpoint carrying customer numbers and invoice references.
  DAB supports Entra ID (`authentication.provider: EntraID`, role-based permissions); forsystem
  would call with its own workload identity.
- **R2 – Broken pagination (silent data loss).** The entity key is `organisation_number` alone, but
  rows are per org × product × day. DAB's `$after` cursor is built from the key, so paging skips
  rows: paging with `$first=500` returned **2 445 of 2 519 rows (74 lost)**, with no error. Likewise
  `GET …/organisation_number/840747972` returns 1 row instead of 59. Fix on their side: declare
  `key-fields` = (`organisation_number`, `product_name`, `transaction_date`) — or a surrogate
  key column — in the DAB entity config. Until then a consumer must fetch in a single page and treat
  any `nextLink` as an error.
- **R3 – `nextLink` is `http://`**, not `https://` (DAB behind a TLS-terminating proxy without
  forwarded headers). Harmless if we never follow it; worth fixing.
- **R4 – Date filter type.** `transaction_date ge '2026-09-01'` → 400; must be
  `transaction_date ge 2026-09-01T00:00:00Z`. Unbounded result order (no default `$orderby`).
- **R5 – Retention window unknown.** If the view keeps only the latest month, a period cannot be
  re-fetched after the next refresh. forsystem must therefore keep its own copy (we do: archived
  raw payload + stored rows).

## Decisions

### D1 — Pull, on demand first, scheduled second
- **Decision**: forsystem pulls from the DAB REST API. Phase A: operator-triggered fetch feeding the
  existing preview → confirm flow. Phase B: a scheduled job (default the 7th, 06:00) runs the same
  fetch for the previous month and *stages* it; a FORVALTER confirms.
- **Rationale**: Reuses the whole validated pipeline; keeps a human on the commit (constitution VII)
  because a re-import replaces the period and is blocked by existing runs; the schedule is a thin
  layer that only supplies the period (constitution IV, docs/06 §4).
- **Alternatives**: *Read DWH PostgreSQL directly* — couples us to their schema and credentials,
  bypasses their API contract; rejected. *DWH pushes to us* — needs an inbound API, auth and a
  contract on their side for no gain at one call per month; rejected. *Fully automatic commit* —
  rejected for now; revisit after a few clean months (it is a config flag away).

### D2 — Aggregate daily → monthly in the adapter; keep the raw payload
- **Decision**: The adapter sums `quantity` (and `amount_nok`) per (orgnr, mapped product, type)
  and emits monthly `RaaBruksrad`s; the full raw JSON is archived through `FileArchive` with
  SHA-256 and linked from `bruksdata_import`.
- **Rationale**: `bruksdata` stays monthly (one row per natural key, generation unchanged); daily
  detail is preserved for audit without a new table (constitution III, R5).
- **Alternatives**: a `bruksdata_dag` staging table — more schema for data nobody queries; rejected
  unless auditors ask for it.

### D3 — Product mapping is data, unmapped names are rejected
- **Decision**: New table `produkt_kildenavn(kilde, kildenavn, produkt_id, type)`, maintained in the
  Produkter UI; several names may map to one product/type (summed). Nothing seeded until confirmed
  (new OQ-18). Example to confirm, not to seed: `Varsling e-post` → `varsling`/BRUKSVOLUM;
  `Altinn studio` vs `altinn-studio-applikasjonsinfrastruktur` → `studio` vs `appinfra` is exactly
  the kind of guess the constitution forbids.
- **Rationale**: Constitution I; names will grow (SMS, Melding).

### D4 — Completeness guard
- **Decision**: One request per period:
  `$filter=transaction_date ge {from}T00:00:00Z and transaction_date lt {to}T00:00:00Z&$first={max}`
  with `max` configurable (default 100 000; the September month is ~2.5k rows). If the response has
  a `nextLink`, fail ("ufullstendig svar"). Re-check every row's date client-side. When R2 is fixed,
  following `nextLink` (rewritten to https) becomes safe; keep the guard behind a flag.
- **Rationale**: R2 makes paging lossy today; a loud failure beats under-invoicing.

### D5 — DWH customer references are a cross-check only
- **Decision**: Compare DWH `kundenummer`/`fakturareferanse` per orgnr with the registry and show
  differences as preview warnings. Do not store them on `bruksdata`; they remain in the raw payload.
- **Rationale**: Registry/CRM is the master (OQ-5, OQ-10); two writers for the same fact is how
  invoices go to the wrong place. OQ-12 asks exactly how usage↔tjenesteeier coupling errors are
  detected — this gives us a cheap detector.

### D6 — Port shape
- **Decision**: Keep `CsvUploadUsageSource` as is and add a `DwhUsageClient` port
  (`hentMaaned(Periode) → DwhSvar(rader, rawBytes, hentetAt)`) used by a new
  `UsageImportService.forhaandsvisFraDwh(Periode)`. Both feed the same private validation.
- **Alternative**: generalise `UsageDataSource.lesRader(periode, InputStream)` so DWH ignores the
  stream — rejected: the `InputStream` parameter is CSV-shaped and the DWH also returns a raw
  payload and warnings the CSV path doesn't have.
  `RaaBruksrad.linjenr` becomes a generic reference (`"orgnr/produktnavn"` for DWH rows) in the
  reject report.
- **HTTP**: Spring `RestClient` (already on the classpath via spring-boot-starter-web). Auth:
  `none` | `entra`, selected by config. `entra` exchanges the AKS workload-identity federated token
  for an access token with a plain OAuth2 client-credentials call in `usage.adapter` — no Azure SDK on
  the compile classpath (`azure-identity-extensions` stays runtime-only, as the pom intends).

### D7 — Staged imports must be persisted
- **Decision**: Today the preview lives in the HTTP session; a scheduled fetch has no session. Persist
  staged imports as `bruksdata_import.status = 'MOTTATT'` (status already exists) plus the archived
  payload; the preview is recomputed from the archived payload when opened, so no staging-rows table
  is needed. Confirming re-validates (mapping or registry may have changed since staging).

### D8 — Testing without the DWH
- **Decision**: `MockRestServiceServer`/WireMock-style fixtures with **fake** orgnrs (never the
  real payload in the repo). Contract test pins the DAB response shape. Local `docker compose` gets
  `forsystem.dwh.enabled=false` by default; a profile can point at a tiny fixture server.

## New open questions (to add to docs/07)

| Id | Question | Owner |
|---|---|---|
| OQ-18 | Mapping DWH `product_name` → forsystem produkt + type (incl. Studio vs app-infra, SMS Norge/utland, e-post vs SMS under Varsling) — who confirms? | Produkteiere + DWH |
| OQ-19 | DAB API: (a) add Entra auth and grant forsystem's identity; (b) fix entity key (R2); (c) https `nextLink` (R3); (d) a test/tt02 endpoint with non-production data | DWH/platform |
| OQ-20 | View semantics: retention window (only latest month?), refresh time of the materialized view, and whether a closed month can still change after the 6th | DWH |
| OQ-21 | `quantity` units per product (e.g. Autorisasjon up to 11.2M/day for one org — billable unit or raw calls?) | Produkteiere + Økonomi |
