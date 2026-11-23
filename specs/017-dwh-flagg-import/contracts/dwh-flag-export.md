# Input contract: `Mv_altinn_usage_last_3m_flag` (as observed 2026-11-06 v2; not yet agreed — OQ-25)

CSV, UTF-8, comma, every text field double-quoted, bare `NULL` for missing, header row, one day per row.
Matched by column name, case-insensitive; order and extra columns do not matter.

| Column | Required | Notes |
|---|---|---|
| `organisation_number` | yes | 9 digits on used rows; may be empty / `NULL` / `unknown` / `owner` / a name on unused rows |
| `organisation_name` | no | lower-case name; used for customer creation from used rows |
| `product_name` | yes | mapped via Produkter → Kildenavn (OQ-18) |
| `quantity` | yes | integer ≥ 0, `NULL` allowed; empty on appinfra |
| `amount_nok` | yes | decimal, only on appinfra rows |
| `transaction_date` (or `transaction_month` / `maaned`) | yes | `YYYY-MM-DD` |
| `source_table` | no | origin table in the DWH |
| `resource_id` | no | may carry `urn:altinn:resource:`; `NULL`/empty on most products |
| `fakturareferanse`, `kundenummer`, `bestillingsnummer` | no | cross-check only |
| `flag_inv_altinn` | no | `1`/`0`. **Present ⇒ flagged file** (used rows = 1) |
| `flag_inv_dpv` | no | `1`/`0`. Counted and reported; not billed here (OQ-26) |
| `regel` | no | `; `-separated `key=value` explanation; shown verbatim |

Row classes: see data-model.md. A file without `flag_inv_altinn` is processed exactly as before.
