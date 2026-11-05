# Data Model: The complete event log

No schema change. `hendelseslogg` (V5) is read as it is.

## Hendelsesfilter (value object)

| Field | Type | Meaning |
|---|---|---|
| fra | LocalDate | first day included (Europe/Oslo), optional |
| til | LocalDate | last day included (Europe/Oslo), optional |
| bruker | String | part of the username, case-insensitive, optional |
| entitet | String | exact entity type, optional |
| handling | String | exact action, optional |

`erTom()`, `gyldig()` (fra ≤ til), `query(side)` for links.

## Side (page of rows)

`rader`, `side`, `antallSider`, `totalt`, `fraRad`, `tilRad`.

## Download columns

| Column | XLSX type | Source |
|---|---|---|
| tidspunkt | date-time (Europe/Oslo) | `tidspunkt` |
| bruker | text | `bruker` |
| handling | text | `handling` |
| entitet | text | `entitet` |
| entitet_id | number | `entitet_id` |
| periode | text `yyyy-MM` | `detaljer.periode` when present |
| detaljer | text | `Detaljtekst.les(detaljer)` |
| detaljer_json | text | `detaljer` as stored |
