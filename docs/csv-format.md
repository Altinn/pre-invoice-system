# CSV format — usage import (bruksdata)

CSV upload is one of two usage sources. The other is the datavarehus API ("Hent fra datavarehus",
specs/001-dwh-usage-import), which feeds the same validation pipeline. CSV stays as the permanent
fallback for when the DWH is unavailable or a period needs a manual correction.

## File

- Encoding **UTF-8** (a leading BOM is tolerated).
- Delimiter **comma** (`,`). Decimal separator **dot** (`.`). No thousands separators.
- One **header row** (column order is free; columns are matched by name, case-insensitive).
- One measurement per row.

## Columns

| Column | Required | Meaning |
|---|---|---|
| `periode` | yes | Billed month as `YYYY-MM-01` (the first day of the month). Must equal the period declared on upload. |
| `organisasjonsnummer` | yes | The usage-generating entity. Exactly 9 digits. |
| `produktkode` | yes | One of the registered product codes: `melding`, `formidling`, `varsling`, `autorisasjon`, `studio`, `appinfra`. |
| `type` | yes | `BRUKSVOLUM`, `AZURE_KOSTNAD`, or `SMS_KOSTNAD`. |
| `antall` | for `BRUKSVOLUM` | Usage volume (decimal ≥ 0). Leave empty for cost rows. |
| `belop` | for cost types | Pass-through amount in NOK (decimal ≥ 0). Leave empty for `BRUKSVOLUM`. |

### Example

```csv
periode,organisasjonsnummer,produktkode,type,antall,belop
2027-01-01,123456789,melding,BRUKSVOLUM,1500,
2027-01-01,123456789,varsling,SMS_KOSTNAD,,842.50
2027-01-01,987654321,appinfra,AZURE_KOSTNAD,,12030.00
```

## Validation (all-or-nothing)

Every row is checked; the staging report lists each rejected row with its file line number and
reason. **A file with any rejected row cannot be imported** — nothing is persisted until the whole
file is clean. Rules:

- **wrong period** — a row's `periode` differs from the declared upload period;
- **malformed orgnr** — not exactly 9 digits;
- **unknown product** — `produktkode` is not a registered product;
- **invalid type** — `type` is not one of the three allowed values;
- **missing / non-numeric / negative amount** — `antall` (volume) or `belop` (cost) as required by `type`;
- **duplicate** — two rows share the same (periode, orgnr, produkt, type).

Import is also **blocked** (independent of row validity) when a non-FORKASTET invoicing run already
exists for the period — forkast that run before re-importing.

## Re-import

Re-uploading a period **supersedes** it: the previous import is marked `AVVIST` and its rows are kept,
read-only, so a discarded run stays traceable (docs/03 §4, specs/002). Only the latest `VALIDERT`
import counts as the period's usage. Within one file, (periode, orgnr, produkt, type) must be unique.

## The datavarehus's own export

While the DWH API is unreachable (OQ-19), a CSV export of the view `mv_altinn_usage_monthly` can be
uploaded as it is, under *Bruksdata → Last opp eksport fra datavarehuset*. It is **not** this format:
it keeps the view's columns (`organisation_number`, `organisation_name`, `product_name`, `quantity`,
`amount_nok`, `kundenummer`, `fakturareferanse`, `bestillingsnummer`, and `maaned` or
`transaction_date`), double-quoted text and the literal `NULL`. Product names are mapped under
*Produkter → Kildenavn*, exactly as for an API fetch, and missing customers can be created from the file.

## Provisional — SMS and Azure (OQ-3, OQ-4)

The `SMS_KOSTNAD` and `AZURE_KOSTNAD` rows and the `belop` semantics are **provisional**. The
authoritative source and delivery format for SMS costs is unresolved (OQ-3, Varsling vs datavarehus),
and Azure cost delivery follows the datavarehus contract (OQ-4). Column names and rules for these two
types may change once those questions are answered; `BRUKSVOLUM` is stable.
