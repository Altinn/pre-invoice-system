# UI contract: fakturareferanse

## GET `/kjoringer/{id}/kontroller` — LESER

- Findings table: `MANGLER_FAKTURAREFERANSE` grouped like other codes; «Hva betyr kodene?» explains it.
- Section `#uten-fakturareferanse` «Fakturaer uten fakturareferanse»:
  - Empty: «Alle fakturaene har fakturareferanse (Deres referanse).»
  - Otherwise: count and what to do; table Kunde · Orgnr · Kundenummer · Produkter uten referanse
    («Alle (…)» when no line has one) · Sum (eksl. mva) · «Linjer» (→ `/kjoringer/{id}/faktura/{fakturaId}`);
    buttons «Last ned listen (XLSX)» and «CSV».

## GET `/kjoringer/{id}/uten-fakturareferanse.{csv|xlsx}` — LESER

Columns `periode, ordre, kundenummer, kundenavn, organisasjonsnummer, produkter_uten_fakturareferanse,
linjer_uten_fakturareferanse, sum_eksl_mva`. File name `uten-fakturareferanse-<yyyy-mm>[-v<n>]-<status>`.
Logged as `LASTET_NED` with `liste` = `uten-fakturareferanse`.

## GET `/kunder/{id}`

Fakturareferanser table: an empty bestillingsnummer next to a fakturareferanse reads
«— (fakturareferansen brukes)»; the help text says so.
