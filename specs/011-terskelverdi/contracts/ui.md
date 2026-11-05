# UI contract: invoice threshold

## GET `/prisversjoner/{id}`

- Shows «Terskelverdi: 40,00 kr eksl. mva per kunde per måned» with a short explanation.
- UTKAST: form `POST /prisversjoner/{id}/terskelverdi` (`terskelverdi`, number ≥ 0, step 0.01),
  button «Lagre terskelverdi». Other statuses: read-only.

## POST `/prisversjoner/{id}/terskelverdi` — FORVALTER

Stores and redirects to the version. Negative/empty value or a non-UTKAST version: `Regelbrudd`
(error page, as for prices). Logged as `ENDRET` on `PRISVERSJON` with `terskelverdi` fra/til.

## GET `/kjoringer/{id}/fakturaer`

- Existing list, plus «Last ned (XLSX)» / «(CSV)» → `/kjoringer/{id}/fakturaliste.{xlsx|csv}`.
- Section `#under-terskel` «Faktureres ikke (under terskelverdi)»: the threshold, count, total;
  table Kunde · Orgnr · Kundenummer · Sum (eksl. mva) · Kundens sum (eksl. mva); downloads
  `/kjoringer/{id}/under-terskel.{xlsx|csv}`. Empty: «Ingen kunder var under terskelverdien.»

## Downloads — LESER

CSV/XLSX, amounts as numbers. Logged as `LASTET_NED` with `liste` = `FAKTURAER` / `UNDER_TERSKEL`.
