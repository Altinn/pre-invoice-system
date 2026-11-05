# Data model: Invoice texts per invoicing run

## `fakturakjoring` (V13)

| Column | Type | Rule | LG04 field |
|---|---|---|---|
| `topptekst` | text, nullable | `char_length <= 120` | `long_info1`, offset 2509, every line |
| `bunntekst` | text, nullable | `char_length <= 120` | `long_info2`, offset 2629, every line |
| `var_referanse` | text, nullable | `char_length <= 25` | `responsible` 3264 and `responsible2` 3289, header line |

Check `fakturakjoring_tekster_satt`: `status not in ('GENERERT','GODKJENT')` or all three are not
null. Backfill: GENERERT/GODKJENT runs get the defaults (header from the run's `periode`).

## `Fakturatekster` (value object, billing)

| Field | Default | LG04 limit | Printed limit (advisory) |
|---|---|---|---|
| topptekst | «Bruk av Altinn » + period label, e.g. «Bruk av Altinn januar 2027» | 120 | 90 — `TODO(OQ-23)` |
| bunntekst | «Se samarbeid.digdir.no for priser og innsyn i egen statistikk» | 120 | 65 — `TODO(OQ-23)` |
| varReferanse | `Servicedesk@altinn.no` | 25 | unknown |

Validation (after trimming each value): not empty; not longer than the LG04 limit; no control
characters (line break, tab…); every character representable in Windows-1252.

## State and lock

| Run status | Texts | Editable |
|---|---|---|
| GENERERT | set at generation | yes (FORVALTER) |
| GODKJENT | frozen | no |
| EKSPORTERT | as written to the file (NULL if exported before V13) | no |
| FORKASTET | as last stored (NULL if discarded before V13) | no |

## Audit (`hendelseslogg`)

`handling = ENDRET`, `entitet = FAKTURAKJORING`, `entitet_id = run id`, detaljer:

```json
{"periode": "2027-01-01", "versjon": 1,
 "endringer": {"bunntekst": {"fra": "Se samarbeid.digdir.no …", "til": "Nye priser fra mars …"}}}
```

Only changed fields are listed; a save without changes writes no row.
