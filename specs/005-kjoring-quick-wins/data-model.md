# Data model: A run page that says what to do next

No schema change and no migration. Two additions on the Java side:

- **`Fakturakjoring.periodeLabel()`** (derived): the period as Norwegian month and year,
  "januar 2027". It comes from the stored `periode` (the first day of the month).
- **`web.Funngruppe`** (view record, never stored):

  | Field | Meaning |
  |---|---|
  | `alvorlighet` | `BLOKKERENDE` or `ADVARSEL` |
  | `kode` | finding code, e.g. `MANGLER_KUNDE` |
  | `meldinger` | every message of that code and severity, in the order they were found |

  The derived values are `antall()`, `blokkerende()` and `eksempel()` (the first message).

  **Order**: BLOKKERENDE before ADVARSEL, then `antall` descending, then `kode` ascending.
