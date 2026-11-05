# Research: Invoice threshold

## R1 — Threshold on the price version

- **Decision**: `prisversjon.terskelverdi numeric(12,2)`, default 40,00 for new versions, editable
  only while UTKAST, locked on activation; existing versions backfilled to 40,00.
- **Rationale**: Økonomi wants it adjustable "på naturlig plassering", rarely. The price version is
  already the date-controlled, activation-locked, audited set of price-model parameters, and every
  run references the version it used, so regenerating January in March applies January's rule.
- **Alternatives**: A global setting (simple, but a change would silently alter regeneration of
  earlier periods); configuration per environment (not editable by Økonomi).
- **NULL** means no threshold. It is not reachable from the UI (the form requires a value); it keeps
  older fixtures and the 5-field constructor working.

## R2 — Left-out customers get no `faktura` row

- **Decision**: Skip their groups before invoices are created; store them in `faktura_under_terskel`.
- **Rationale**: Every consumer of `faktura` (LG04, PDF, spreadsheets, sums, previous-period
  comparison, LG04 reconciliation) then stays correct without a filter. A flag on `faktura` would
  have to be honoured in all of them, and `ordre_nr` would get gaps.

## R3 — One finding per run, linked to every left-out usage row

- **Decision**: One ADVARSEL `UNDER_TERSKEL` («N kunder har samlet X kr eksl. mva … faktureres ikke»)
  whose `kontrollfunn_bruksdata` links cover all usage rows of the left-out customers. Each
  `faktura_under_terskel` row references the finding.
- **Rationale**: Many small customers would otherwise produce many warnings and bury the real ones;
  the list on the Fakturaer tab carries the detail. The link keeps the usage reconciliation's
  invariant (imported = invoiced + explained) intact.

## R4 — Per customer, strictly greater

- **Decision**: Sum all would-be invoices of a `kunde` (all kundenumre, all products); invoice only
  when the sum is greater than the threshold (`compareTo > 0`).
- **Rationale**: FEL's SQL sums per organisasjonsnummer and keeps `> 40` (OQ-22).

## R5 — Correction runs are exempt

- **Decision**: No threshold in `genererKorrigering`.
- **Rationale**: Correction lines are signed differences against an exported run; thresholding a
  difference has no agreed meaning, and correction export is disabled until OQ-6 is settled.

## R6 — Downloads

- **Decision**: `GET /kjoringer/{id}/fakturaliste.{csv|xlsx}` and `/kjoringer/{id}/under-terskel.{csv|xlsx}`
  via the existing `RegnearkEksport`, logged as `LASTET_NED`.
- **Rationale**: Same mechanism as the fakturagrunnlag download. PDF is left out: the request lists it
  among examples, CSV/XLSX serve sorting and filtering, and the page prints.
