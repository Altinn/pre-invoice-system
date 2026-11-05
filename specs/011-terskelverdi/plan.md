# Implementation Plan: Invoice threshold (terskelverdi)

**Branch**: `feat/011-terskelverdi` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

## Summary

Add `terskelverdi` to `prisversjon` (default 40,00, editable while UTKAST, locked on activation). In
generation of an ordinary run, after usage is grouped into would-be invoices, sum per customer; a
customer whose total is not over the threshold gets no `faktura` rows. Instead the run stores one
`faktura_under_terskel` row per would-be invoice and one ADVARSEL `UNDER_TERSKEL` finding, linked
to all their usage rows through `kontrollfunn_bruksdata` so the usage reconciliation explains them.
Because no `faktura` row exists for them, the export, PDFs, spreadsheets and sums need no change.
The Fakturaer tab lists them, and both lists download as CSV/XLSX. One migration (V14).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx,
Designsystemet CSS, Apache POI (existing `RegnearkEksport`)

**Storage**: PostgreSQL 16 / Flyway — `V14__terskelverdi.sql`: `prisversjon.terskelverdi
numeric(12,2)` (backfilled 40.00), table `faktura_under_terskel`

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers; LG04 golden file unchanged

**Target Platform**: Linux container (dis-core tt02/prod); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: Generation stays one pass over in-memory groups

**Constraints**: billing period explicit; humans approve (warning only); no invented values (40 kr and
the rule `> 40` per orgnr are FEL's/Økonomi's, OQ-22)

**Scale/Scope**: A few hundred customers per month; possibly many small ones

## Constitution Check

*Gate before Phase 0: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. 40 kr ex. mva, per orgnr, strictly greater: FEL 2026-11-04 and Økonomi's kickoff decision (OQ-22). Carry-over is not invented: FEL's program drops the amounts, and the open part stays in OQ-22. |
| II. LG04 Output Is Contractual | PASS. The writer is untouched; left-out customers simply have no order. Golden file unchanged. |
| III. Traceability Is Structural | PASS. Every left-out usage row is linked to the finding; each would-be invoice is stored with its amount; the threshold is part of the locked price version the run references. |
| IV. Explicit Billing Period | PASS. The threshold comes from the price version that covers the run's period, never from today's date. |
| V. Portability Behind Ports | PASS. No new dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret. |
| VII. Humans Approve Money | PASS. The left-out customers are a visible warning before approval; the threshold change is audited and locked on activation. |

## Project Structure

```text
specs/011-terskelverdi/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
├── contracts/ui.md
└── checklists/requirements.md

src/main/resources/db/migration/V14__terskelverdi.sql
src/main/java/no/digdir/forsystem/
├── registry/Prisversjon.java, PrisversjonService.java      # terskelverdi, settTerskelverdi
├── billing/Kontrollkode.java                                 # + UNDER_TERSKEL
├── billing/FakturaUnderTerskel.java, …Repository.java        # NEW
├── billing/FakturakjoringService.java                        # threshold in bygg(); underTerskel()
├── billing/NullstillingService.java                          # delete the new rows
├── export/EksportService.java                                # fakturaliste / underTerskel downloads
└── web/PrisversjonController.java, FakturakjoringController.java
src/main/resources/templates/prisversjon/detalj.html, kjoring/fakturaer.html
src/test/java/no/digdir/forsystem/billing/TerskelverdiIT.java   # NEW
docs/07-open-questions.md, docs/brukerveiledning.md, docs/funksjonsoversikt.md,
docs/03-datamodel.md, docs/09-beslutningslogg.md
```

**Structure Decision**: Existing modules. The threshold is a price-model parameter, so it lives with
the prices and inherits their lifecycle; the check belongs in generation next to the other controls.

## Complexity Tracking

No constitution violations to justify.
