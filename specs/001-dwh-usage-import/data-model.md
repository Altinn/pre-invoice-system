# Data model: Usage import from the datavarehus API

One Flyway migration, `V7__dwh_bruksdata.sql`. Existing tables are extended, not replaced;
`bruksdata` (the monthly natural key) is unchanged, so generation is untouched.

## New: `produkt_kildenavn` — DWH product-name mapping (FR-004, D3)

| Column | Type | Rules |
|---|---|---|
| `id` | bigint identity PK | |
| `kilde` | text | `'DWH'` (check); room for other sources |
| `kildenavn` | text | exact `product_name` from the DWH, case-sensitive |
| `produkt_id` | bigint → `produkt` | |
| `type` | text | `BRUKSVOLUM` \| `AZURE_KOSTNAD` \| `SMS_KOSTNAD` |
| | | `unique (kilde, kildenavn)` — one name maps to exactly one product/type; many names may share one |

No seed rows (OQ-18). Changes are audit-logged (`OPPRETTET`/`ENDRET`/`SLETTET`, entity
`PRODUKT_KILDENAVN`).

## Changed: `bruksdata_import`

| Column | Change | Purpose |
|---|---|---|
| `kilde` | unchanged (`'DWH'` already allowed) | |
| `status` | unchanged values; `MOTTATT` now used for staged scheduled fetches | D7 |
| `raadata_url` | **new**, text null | archived raw payload location (`FileArchive`) — required when `kilde='DWH'` |
| `raadata_sha256` | **new**, text null | checksum of the raw payload |
| `hentet_at` | **new**, timestamptz null | when the DWH was read |
| `filnavn` | for DWH: `dwh:mv_altinn_usage_monthly:<periode>` | keeps the not-null column meaningful |

Check: `kilde <> 'DWH' or (raadata_url is not null and raadata_sha256 is not null)`.

### State transitions

```
            (manual fetch)            confirm
  ─────────────────────────────►  VALIDERT  ──(re-import)──► AVVIST
  (scheduled fetch) ─► MOTTATT ──confirm──►  VALIDERT
                        │
                        └──(newer fetch / discard)──► AVVIST
```

- Manual fetch keeps today's flow (session preview → confirm creates `VALIDERT`), but now archives
  the payload on confirm.
- Scheduled fetch archives the payload immediately and creates `MOTTATT` with no `bruksdata` rows.
  Opening it recomputes the preview from the archived payload; confirming validates again and
  inserts rows, moving it to `VALIDERT` (same replacement rule as today).
- At most one `MOTTATT` per period: a newer staged fetch marks the older one `AVVIST`.

## Transient: DWH row (adapter-internal)

`DwhRad(organisationNumber, productName, quantity, amountNok, transactionDate, kundenummer,
fakturareferanse)` — parsed from the response, aggregated to `RaaBruksrad`, never persisted outside
the archived payload.

## Aggregation rule (FR-002)

Group by (`organisation_number`, mapping(`product_name`) → (produkt, type)); sum `quantity` for
`BRUKSVOLUM`, `amount_nok` for cost types. Rows whose name is unmapped are not aggregated — one
reject per (orgnr, name). Rows outside the period: one reject each.

## Preview warnings (FR-007, new `advarsler` on `Forhaandsvisning`)

- DWH `kundenummer`/`fakturareferanse` ≠ registry for that orgnr.
- Per product, total differs > ±50 % (configurable) from the previous `VALIDERT` import, or a product
  present last month is absent.
Warnings never block confirmation.
