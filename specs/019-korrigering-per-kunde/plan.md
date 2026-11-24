# Implementation Plan: Corrections per customer

**Branch**: `019-korrigering-per-kunde` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-korrigering-per-kunde/spec.md`

## Summary

Extend the specs/007 correction workflow from product rows to customers. The comparison gains a
customer level (`bruksdata_kundeavvik`) that holds the invoiced and candidate customer values and the
customer-level categories (Endret fakturareferanse / bestillingsnummer / kundenummer / navn), so a
reference change with difference 0 is a deviation. The page groups by customer with an expandable
product list; selection and dismissal move to the customer. «Sammenlign på nytt» recomputes the
retained candidate against the current effective basis. Delta generation refuses customers with
customer-level changes. Credit and re-invoice (method KREDIT_OG_NY) and the credited invoice number
are reserved in the schema/enum only and stay disabled until OQ-6/OQ-29 are answered; no LG04 offset
is touched.

Delivery: stories 1–3 in one PR (they share the schema and page), story 4 in a second PR, stories
5–6 only after OQ-6/OQ-29 answers, with their own plan update.

## As built (stories 1–3)

Deviations from the design below, decided while implementing:

- No `korrigeringskjoring_kunde` table: the selected customers follow from the selected product
  changes (`bruksdata_endring.kundeavvik_id`), and a customer cannot be ticked without its changes.
- No `korrigeringsmetode` column yet: it only matters for story 5 and is added with it.
- The candidate's customer values are copied onto `bruksdata_kundeavvik` (no FK to `bruksdata_kunde`);
  both are insert-only.
- `ENDRET_NAVN` does not refuse a delta (the name is not in LG04); FR-011 was narrowed accordingly.
- The net difference in kroner is computed for display from the baseline run's price version
  (`FakturakjoringService.enhetspriser`), rounded per line as generation does; it is not stored.
- «Velg alle» posts `alle=true`, resolved server-side to every customer of the comparison.
- Expand/collapse is a button with `aria-expanded`/`aria-controls`; without JavaScript the product rows
  are shown (a `<noscript>` style).

## As built (story 4)

- V20 adds `avvist_av/at/aarsak` to `bruksdata_kundeavvik`; the customer's product changes get the same
  values in their V12 columns, so the generation check on `avvist_at` keeps working.
- «Sammenlign på nytt» lives on the comparison page (Bruksdata links there); it compares the period's
  latest candidate, refuses while a run is GENERERT/GODKJENT, and the page warns when a newer version
  has been exported since the comparison was made.
- Dismissal is per customer on the page (a `<details>` with its own form via the `form` attribute, since
  forms cannot nest); the service takes a list.
- Carry-over compares a fingerprint of the customer deviation (categories, invoiced and candidate values,
  and each product change's old/new value) with the latest earlier comparison of the same candidate.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx,
Digdir Designsystemet

**Storage**: PostgreSQL 16, Flyway (next migration: `V19__korrigering_per_kunde.sql`)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL; extend `BruksdataDifferanseTest`
and `KorrigeringskjoringIT`; learning-center link/control-code build checks (specs/016)

**Target Platform**: Linux container (dis-core); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: 300 customers / 2 000 changes: first page < 2 s (SC-001)

**Constraints**: ordinary LG04 byte-identical; correction export gate stays closed; no invented
accounting values; explicit period; history immutable

**Scale/Scope**: a few hundred customers per period, 1–3 corrections per month

## Constitution Check

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. Credit mechanism, LG04 field for the credited invoice number, its format and mandatoriness are OQ-6/OQ-29; only `DELTA` is enabled, and nothing new is written to LG04. |
| II. LG04 Output Is Contractual | PASS. No change to `Lg04Skriver` or golden files in stories 1–4. |
| III. Traceability Is Structural | PASS. Customer deviations link to the comparison, the candidate's `bruksdata_kunde` row and the invoiced `faktura`; dismissals are kept, not deleted. |
| IV. Explicit Billing Period | PASS. Compare-again takes the period from the comparison/candidate, never from the clock. |
| V. Portability Behind Ports | PASS. No external system involved; compare-again reads the retained candidate. |
| VI. Secrets From Configuration Only | PASS. No secrets. |
| VII. Humans Approve Money | PASS. Nothing preselected; dismissal and generation need a reason; approval unchanged. |

## Design

### Comparison model

`BruksdataDifferanse.sammenlign` stays a pure product-level diff. A new pure function
`KundeavvikBygger` takes the product changes, the invoiced customer values per orgnr and the
candidate's customer values per orgnr, and returns one `Kundeavvik` per orgnr that has any product
change **or** any customer-level difference. Unchanged-customer count = orgnrs in either basis minus
deviations.

Customer-value normalisation: `strip()`, empty → null; compare with `Objects.equals` (case-sensitive).

**Invoiced customer values** (old side): for each orgnr, the invoice lines of the **latest exported
run in the effective chain that invoiced that orgnr** (V1, or a correction that touched it). From
`faktura` (kundenummer, organisasjonsnummer, kundenavn snapshots) and `fakturalinje` (fakturareferanse,
bestillingsnummer snapshots). If the lines carry more than one distinct reference (pre-specs/018
product-specific references), the old value is the sorted distinct set and any candidate value
different from a singleton set is a change. Lines without a snapshot → `ukjent` (always a deviation).
Implemented as a read in `KjoringStatusPort` (billing owns invoices; usage must not query billing
tables directly), e.g. `fakturerteKundeverdier(periode, sisteEksporterteKjoringId)`.

**Candidate customer values**: `bruksdata_kunde` rows of the candidate import (stored since PR #58).
A removed customer missing there uses the invoiced values for display (FR-002).

### Selection

The form posts `orgnr` values (not `endringId`). `genererKorrigering` resolves them to the customer
deviations of the open comparison, checks they are pending, refuses (FR-011) if any has a
customer-level category while method = DELTA, then expands to their `bruksdata_endring` ids and
continues as today. `korrigeringskjoring_endring` is unchanged; a new
`korrigeringskjoring_kunde(kjoring_id, kundeavvik_id)` records the selected customers (FR-010).

«Velg alle» across pages: a `velgAlle=true` form flag resolved server-side to every pending customer
of the comparison, with a confirmation of the count; the client-side live totals use the summary
totals in that case.

### Compare again

`BruksdataKandidatService.sammenlignPaaNytt(sammenligningId)`: loads the comparison's candidate,
requires no GENERERT/GODKJENT run for the period, gets the current `EffektivtGrunnlag`, and runs the
existing `opprett` path with the stored candidate (the idempotency key `(kandidat, grunnlag)` already
exists). The candidate status `BEHANDLET` must be accepted as input. Dismissal carry-over (FR-016):
after building the new deviations, copy a dismissal from the superseded comparison of the same
candidate when the customer's set of (key, old, new) and customer-level (category, old, new) is
equal.

Shown on the comparison page and on Bruksdata for the period when the latest run is EKSPORTERT or
FORKASTET and a candidate exists. POST `/bruksdata/sammenligninger/{id}/paa-nytt` (FORVALTER).

### Dismissal

POST `/bruksdata/sammenligninger/{id}/avvis` with `orgnr[]` + `aarsak`; POST `.../gjenopprett` with
`orgnr`. Dismissal is stored on the customer deviation (`avvist_av/at/aarsak`); the existing columns
on `bruksdata_endring` are filled for its changes too, so specs/007's generation check
(`avvistAt() != null`) keeps working. Restore clears both and logs `Handling.GJENOPPRETTET` (new
audit action) with the reason of the original dismissal in the details.

### Method seam (stories 5–6)

`fakturakjoring.korrigeringsmetode` (`DELTA` default for corrections, null for ordinary runs, check
constraint allowing `DELTA` and `KREDIT_OG_NY`). The service refuses `KREDIT_OG_NY` with
`TODO(OQ-6)`. No column for the credited invoice number and no credit-invoice link are added now:
their shape depends on OQ-29 (c, h) (one per customer or per credited invoice). Stories 5–6 get a
plan amendment when answered.

### UI

`templates/bruksdata/sammenligning.html`: table of customers; each row a `<details>`-free disclosure
button (`aria-expanded`, `aria-controls`) toggling a row group with the product changes (rendered
server-side, hidden; no extra request). Category tags per customer; for reference categories, a
«før → nå» line under the name. Dismissed customers in a separate «Avvist» section with restore.
Delta-refusal message rendered as a `ds-alert` naming the customers.

## Project Structure

### Documentation (this feature)

```text
specs/019-korrigering-per-kunde/
├── spec.md
├── plan.md
├── data-model.md
└── checklists/requirements.md
```

`tasks.md` is produced by `/speckit-tasks` when implementation is scheduled.

### Source Code (repository root)

```text
src/main/java/no/digdir/forsystem/
├── usage/
│   ├── BruksdataDifferanse.java          (unchanged product diff)
│   ├── KundeavvikBygger.java             (new, pure)
│   ├── Kundeavvik.java + Repository      (new, table bruksdata_kundeavvik)
│   ├── Kundeavvikskategori.java          (new enum)
│   ├── BruksdataKandidatService.java     (customer deviations, sammenlignPaaNytt, avvis/gjenopprett)
│   └── KjoringStatusPort.java            (+ fakturerteKundeverdier)
├── billing/
│   ├── FakturakjoringService.java        (select by customer, FR-011 refusal, korrigeringsmetode)
│   └── Korrigeringsmetode.java           (new enum DELTA, KREDIT_OG_NY)
├── common/Handling.java                  (+ GJENOPPRETTET; AVVISTE exists)
└── web/BruksdataSammenligningController.java (grouping, filters, paging by customer, new POSTs)
src/main/resources/
├── db/migration/V19__korrigering_per_kunde.sql
├── templates/bruksdata/sammenligning.html
└── hjelp/slik-gjor-du/lag-korrigeringskjoring.md, hjelp/forsta-systemet/korrigering-og-avregning.md
src/test/java/no/digdir/forsystem/
├── usage/KundeavvikByggerTest.java       (categories, normalisation, sets, unknown old value)
├── usage/BruksdataDifferanseTest.java    (unchanged)
└── billing/KorrigeringskjoringIT.java    (grouping, reference-only, refusal, compare again, dismissal carry-over)
```

**Structure Decision**: Existing single Spring Boot module; usage owns comparison, billing owns
runs and invoices, as in specs/007.

## Test plan

- Unit: `KundeavvikBygger` — each category alone and combined; whitespace/empty normalisation; case
  change is a change; removed/added customer; multi-reference old set; unknown old value.
- IT (`KorrigeringskjoringIT`): reference-only candidate gives one deviation with difference 0;
  tick customer generates all its product deltas and nothing else; reference change refused in DELTA
  with the customers named; export V2 → «Sammenlign på nytt» shows only unticked customers, no fetch;
  discarded V2 → compare again re-lists its customers; dismissal requires reason, blocks selection,
  carries over on same candidate only when identical, not on a new candidate; restore.
- Web (MockMvc): grouped rendering, `aria-expanded`, filters/paging by customer, role checks on new
  POSTs.
- Regression: all LG04 golden files unchanged; ordinary run tests unchanged.
- Learning center: build check for links and any new control code.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| `bruksdata_kundeavvik` table | Customer-level categories and dismissal need a row even when no product changed (difference 0). | Deriving on the fly cannot hold a dismissal or a selection for a reference-only customer. |
| Reserved `korrigeringsmetode` | Makes the DELTA-only behaviour explicit and gives OQ-6 a place to land without a later data migration of existing correction runs. | Adding it later would need a back-fill and leaves FR-011's refusal implicit. |
