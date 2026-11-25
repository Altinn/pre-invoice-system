# Spec 020 — Avregning (settlement of real transaction price)

## Source
Task «Avregning»: also invoice the settlement through forsystem. The settlement rests on the real transaction price
(actual costs and volume). A tab named **Avregning**; a price adjustment on all products except Altinn Studio
applikasjonsinfrastruktur (1:1 between booked cost and invoiced amount); tick which months to settle; a draft run
is generated and handled as usual. Cost = sum of transactions in the settlement period × price adjustment.
Standard text: «Avregning bruk av Altinn 202x».

## Decisions
- **Run type `AVREGNING`**, anchored on 1 January of the settlement year (`periode`), one *pending* settlement per
  year. It sits outside the per-month rules: month-keyed queries and unique indexes ignore it (V21), it has its own
  version counter, and it uses no price version (`prisversjon_id` null).
- **Prisjustering** = kr per unit (may be negative), entered per product. Line amount = Σ usage of the ticked months
  × adjustment, per organisation and product, rounded half up to 2 decimals. Products without an adjustment, and
  cost-type usage rows, are not settled. TODO(OQ-30): the interpretation awaits Økonomi and VIS.
- **`appinfra` is excluded**: invoiced at booked Azure cost with no påslag, so 1:1 already holds. The form shows it
  as «Avregnes ikke» with the reason; the service refuses it.
- Only months with imported usage can be ticked; each month contributes its current import's volumes. Customer data
  comes from the ticked months' imports, newest first.
- The run follows the normal path: controls, approval, texts, export. No threshold. No monthly usage reconciliation
  (it compares one month). A negative invoice blocks LG04 export (OQ-6).
- Header text `Avregning bruk av Altinn <år>`, editable while GENERERT.

## Acceptance
1. The Avregning tab lists the year's months (disabled without usage) and the products, with the exclusion explained.
2. Generating with ticked months and adjustments creates a GENERERT `AVREGNING` run with one invoice per customer;
   amounts match Σ usage × adjustment; the months and adjustments are stored with the run.
3. `appinfra` cannot be settled; no month / no adjustment / no usage / a second pending settlement are refused with
   a reason.
4. The monthly run for January, its versions, corrections and the dashboard are unaffected.
