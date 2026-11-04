# Data model: LG04 export in FEL's Altinn format

No database change. `kontrollfunn.kode` is free text, so the new finding code needs no migration.

## Lg04Ordre (export, in memory — built in c344466)

One LG04 order. Normally one per invoice; one per distinct reference pair when an invoice carries
several (R2).

| Field | Source | Notes |
|---|---|---|
| kundenummer | `faktura.kundenummer` | `apar_id` |
| fakturareferanse | the lines' shared `fakturareferanse` | `accountable`, cut to 25 with "..." |
| bestillingsnummer | the lines' shared `bestillingsnummer` | `ext_ord_ref`, cut to 25 (out of scope to widen) |
| tekst | "Bruk av Altinn " + period label | `long_info1`, ≤ 120 (FR-012) |
| ordreNr | running number in file order | `order_id`; equals `faktura.ordre_nr` unless an earlier invoice was split |
| url | configured base + kundenummer + uuid | `long_info2`, ≤ 120 (FR-012) |
| ansvarlig1/2 | configuration | `responsible`, `responsible2` |
| batchId | export date, yyyyMMdd | `batch_id` (buntnummer) |
| linjer | ordered list of Lg04Ordrelinje | ≥ 1 |

## Lg04Ordrelinje (export, in memory — built)

| Field | Source | Notes |
|---|---|---|
| konto, artikkel, dim1/2/4 | product registry | OQ-2 |
| produktnavn | `produkt.navn` | `art_descr` |
| belopOre | line amount × 100 | `amount` and `cur_amount`, right-aligned in 20 |
| antall | `fakturalinje.antall`, null for cost lines | `value_1`: blank if null, else × 100 half-up (FR-010) |

## Kontrollkode.DELT_REFERANSE (new, ADVARSEL)

Recorded at generation on an invoice whose lines carry more than one distinct (fakturareferanse,
bestillingsnummer) pair. Message names the pairs and says the invoice becomes N invoices in Unit4.
Does not block approval. Not recorded for an invoice with a single pair, including "no reference".

## Kjoringssammendrag (computed, never stored)

| Field | Change |
|---|---|
| sumBelop | unchanged — sum of lines, ex. mva |
| sumInklMva | **new** — Σ over invoices of round_half_up(invoice sum × 1.25, 2) (FR-011) |

## E-mail block (run page, after export)

Område (Altinn) · Buntnummer (export date) · Periode · Antall fakturaer (orders in the file) ·
Samlet beløp eksl. mva · Samlet beløp inkl. mva.
