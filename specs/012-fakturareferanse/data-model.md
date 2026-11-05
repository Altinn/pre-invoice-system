# Data model: Fakturareferanse control and bestillingsnummer fallback

No migration. The schema is unchanged (latest remains V14).

## `kontrollfunn`

New code `MANGLER_FAKTURAREFERANSE` (ADVARSEL). One per invoice with at least one line without
`fakturareferanse`; `faktura_id` is set; no `kontrollfunn_bruksdata` links (no usage is left out).

## `fakturalinje`

`bestillingsnummer` is the registry value, or — when that is empty or blank — the line's
`fakturareferanse` (snapshot at generation). `fakturareferanse` is unchanged.

## `kunde_referanse`

Unchanged; keeps what CRM, the DWH export or the FORVALTER entered.

## Read model `FakturaUtenReferanse` (not stored)

| Field | Source |
|---|---|
| `fakturaId`, `ordreNr`, `kundenummer`, `sumBelop` | `faktura` |
| `kundenavn` | `faktura.kundenavn` (frozen) |
| `organisasjonsnummer` | `kunde` |
| `produkter`, `antallLinjer`, `alleLinjer` | lines with `fakturareferanse is null` |
