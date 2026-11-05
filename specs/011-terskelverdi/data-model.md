# Data model: Invoice threshold (V14)

## `prisversjon`

| Column | Type | Rule |
|---|---|---|
| `terskelverdi` | numeric(12,2), nullable | `>= 0`; new versions 40.00; backfilled 40.00; NULL = no threshold |

## `faktura_under_terskel` (new)

| Column | Type | Note |
|---|---|---|
| `id` | bigint identity | |
| `kjoring_id` | bigint → fakturakjoring | |
| `kunde_id` | bigint → kunde | |
| `organisasjonsnummer` | text | the customer's orgnr at generation |
| `kundenavn` | text | frozen like `faktura.kundenavn` |
| `kundenummer` | text | the would-be invoice's kundenummer |
| `tilleggstekst` | text, nullable | |
| `sum_belop` | numeric(12,2) | the would-be invoice's sum |
| `antall_linjer` | integer ≥ 1 | |
| `kundesum` | numeric(12,2) | the customer's total over all its would-be invoices |
| `terskelverdi` | numeric(12,2) | the threshold that applied |
| `kontrollfunn_id` | bigint → kontrollfunn | the run's `UNDER_TERSKEL` finding |

Unique `(kjoring_id, kunde_id, kundenummer)`.

## `kontrollfunn`

New code `UNDER_TERSKEL` (ADVARSEL), at most one per run, `faktura_id` NULL. Its
`kontrollfunn_bruksdata` rows are every usage row of the left-out customers.
