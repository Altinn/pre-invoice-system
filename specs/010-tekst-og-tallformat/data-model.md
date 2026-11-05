# Data model: Clearer texts, labels and number format

No schema change.

## Kontrollkode (enum, `billing`)

| Field | Type | Meaning |
|---|---|---|
| `alvorlighet` | `Alvorlighet` | Severity the code is always raised with (BLOKKERENDE / ADVARSEL). |
| `definisjon` | `String` | One Norwegian sentence shown on the Kontroller tab. |

Rule: every constant has both. `KontrollkodeTest` enforces this, and the service's finding helpers
refuse a code raised with the other severity.

## Dimensjonsnavn (config, `forsystem.produkt.dimensjoner`)

Defaults change from blank to `Koststed`, `Prosjekt/formål` and `Kappost`. The label format changes
from `Dim 1 (Koststed)` to `Dim1 Koststed`. Without a name, the label is just `Dim1`.
