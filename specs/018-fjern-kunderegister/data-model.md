# Data model: Remove the customer registry (specs/018)

Two Flyway migrations: `V17__kundedata_fra_dwh.sql` (additive: new table, orgnr on invoices, backfills, `kunde_id` nullable) and `V18__dropp_kunderegister.sql` (drops `kunde_id` and the registry tables). Decisions in [research.md](research.md) D1, D5.

## New: `bruksdata_kunde`

The customer values one import delivered for one organisation. Insert-only, like `bruksdata` and
`bruksdata_kilde`. A superseded import keeps its rows.

| Column | Type | Rule |
|---|---|---|
| `id` | bigint identity | PK |
| `import_id` | bigint NOT NULL → `bruksdata_import` | |
| `organisasjonsnummer` | text NOT NULL | as in the usage rows of the same import |
| `virksomhetsnavn` | text | as delivered (the DWH sends lower case); NULL if not delivered |
| `kundenummer` | text | NULL ⇒ MANGLER_KUNDENUMMER at generation |
| `fakturareferanse` | text | NULL ⇒ MANGLER_FAKTURAREFERANSE (warning) |
| `bestillingsnummer` | text | NULL ⇒ fallback to fakturareferanse (specs/012) |
| `motstridende` | text[] NOT NULL default `'{}'` | e.g. `{"kundenummer: 5663 \| 5664"}`; non-empty ⇒ MOTSTRIDENDE_KUNDEDATA |

Constraint: `unique (import_id, organisasjonsnummer)`.

Written: one row per organisation that has at least one **used** usage row in the import (DWH flagged
file: flag-1 rows in the period; older DWH file, API, own CSV: all valid rows). An import is
consistent: every orgnr in its `bruksdata` has exactly one `bruksdata_kunde` row.

## Changed: `faktura`

| Change | Detail |
|---|---|
| + `organisasjonsnummer text NOT NULL` | backfilled from `kunde.organisasjonsnummer` via `kunde_id` |
| `kundenavn` → NOT NULL | backfilled from `kunde.virksomhetsnavn` where NULL (invoices made before V11) |
| − `kunde_id` | nullable in V17, dropped in V18 with `unique (kjoring_id, kunde_id, kundenummer)` |
| + `unique (kjoring_id, organisasjonsnummer, kundenummer)` | new runs have one invoice per orgnr (FR-009). Old runs may have several kundenummer per orgnr, so kundenummer stays in the key |

`tilleggstekst` stays (old invoices have values) and is NULL for new invoices.
`fakturamottaker_orgnr` stays and equals `organisasjonsnummer` for new invoices.

## Changed: `faktura_under_terskel`

`− kunde_id` (nullable in V17, dropped with its unique in V18), `+ unique (kjoring_id, organisasjonsnummer, kundenummer)`.
Already carries orgnr, name and kundenummer.

## Dropped

`kunde_referanse`, `kundenummer_regel`, `kunde`, in that order, in V18. History lives on in `hendelseslogg`
(entities `KUNDE`, `KUNDE_REFERANSE`, `KUNDENUMMER_REGEL`) and in invoice snapshots.

## Unchanged, but read differently

- `fakturalinje.servicekode`: NULL for new lines (no rule supplies it). `fakturareferanse` and
  `bestillingsnummer` are copied from `bruksdata_kunde`.
- `kontrollfunn.kode`: may hold retired codes (`MANGLER_KUNDE`, `INAKTIV_AVTALE`,
  `AKTIV_KUNDE_UTEN_BRUK`) on old runs. New code: `MOTSTRIDENDE_KUNDEDATA`.

## Java

| Type | Change |
|---|---|
| `usage.BruksdataKunde` (+ repository) | NEW record for `bruksdata_kunde` |
| `usage.Kundedata` | NEW value: `(orgnr, navn, kundenummer, fakturareferanse, bestillingsnummer, List<String> motstridende)`. Built by a small collector shared by the DWH file reader, the API reader and the CSV reader (replaces `DwhCsv.KundeIFil`) |
| `usage.DwhRad` | + `navn`, `bestillingsnummer`; javadoc no longer says «cross-check only» |
| `usage.RaaBruksrad` | + optional customer fields for the own CSV format |
| `usage.Forhaandsvisning` | + `List<Kundedata> kunder` (persisted on import, conflicts counted in the preview) |
| `billing.Faktura` | − `kundeId`, + `organisasjonsnummer` |
| `billing.FakturaUnderTerskel` | − `kundeId` |
| `billing.Kontrollkode` | + `MOTSTRIDENDE_KUNDEDATA`; three codes marked retired |
| `registry.Kunde*`, `Avtalestatus`, `KundeService`, `Brreg*` | DELETED |
