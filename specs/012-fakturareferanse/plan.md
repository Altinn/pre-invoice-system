# Implementation Plan: Fakturareferanse control and bestillingsnummer fallback

**Branch**: `claude/012-fakturareferanse-3dd86d` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

## Summary

At generation, snapshot each line's reference pair through `Referansepar.av`: an empty or blank
bestillingsnummer takes the fakturareferanse. The registry is untouched. Because the rule maps (r, ∅) to
(r, r), it can only merge LG04 reference groups, never add a split. After an invoice is created, record
one ADVARSEL `MANGLER_FAKTURAREFERANSE` if any of its lines has no fakturareferanse. The Kontroller tab
gets a section «Fakturaer uten fakturareferanse», read from the run's lines, with XLSX/CSV downloads
through the existing list endpoint. No migration.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx,
Designsystemet CSS, Apache POI (existing `RegnearkEksport`)

**Storage**: PostgreSQL 16 / Flyway — no schema change (latest stays V14)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers; LG04 golden file unchanged

**Target Platform**: Linux container (dis-core tt02/prod); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: Generation stays one pass; the list reads a run's lines once

**Constraints**: billing period explicit; humans approve (warning only); no invented values

**Scale/Scope**: A few hundred invoices per month

## Constitution Check

*Gate before Phase 0: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. The fallback and the warning are Økonomi's kickoff wishes. The two choices they did not state (product-row inheritance, registry-wide list) have defaults marked `TODO(OQ-24)`. Severity follows «tilsvarende Aktiv_kunde_uten_bruk»; OQ-15 keeps the mandatory-field list. |
| II. LG04 Output Is Contractual | PASS. Writer untouched; only the values in existing fields change. Golden file unchanged. |
| III. Traceability Is Structural | PASS. The snapshot holds the value sent; the registry holds the value delivered; the finding is linked to its invoice. |
| IV. Explicit Billing Period | PASS. Nothing date-dependent. |
| V. Portability Behind Ports | PASS. No new dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret. |
| VII. Humans Approve Money | PASS. A visible warning before approval; nothing blocks or changes silently in the registry. |

## Project Structure

```text
specs/012-fakturareferanse/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
├── contracts/ui.md
└── checklists/requirements.md

src/main/java/no/digdir/forsystem/
├── billing/Kontrollkode.java               # + MANGLER_FAKTURAREFERANSE
├── billing/FakturaUtenReferanse.java       # NEW read model
├── billing/FakturakjoringService.java      # Referansepar fallback, finding, utenFakturareferanse()
├── export/EksportService.java              # list "uten-fakturareferanse"
├── web/FakturakjoringController.java       # model attribute + download path
└── demo/Demodata.java                      # Oslo kommune: fakturareferanse only
src/main/resources/templates/kjoring/kontroller.html, kunde/detalj.html
src/test/java/no/digdir/forsystem/billing/FakturareferanseIT.java   # NEW
docs/07-open-questions.md, docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/09-beslutningslogg.md
```

**Structure Decision**: Existing modules; the fallback sits where references are already chosen
(`velgReferanse`), and the control next to `DELT_REFERANSE`, which uses the same pairs.

## Complexity Tracking

No constitution violations to justify.
