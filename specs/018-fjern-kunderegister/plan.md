# Implementation Plan: Remove the customer registry — customer data comes from the DWH

**Branch**: `018-fjern-kunderegister` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-fjern-kunderegister/spec.md`

## Summary

Each usage import stores the customer values it carries (name, kundenummer, fakturareferanse,
bestillingsnummer) per organisation in a new insert-only table `bruksdata_kunde`. Generation reads them
from the run's own import instead of the registry: one invoice per orgnr, fakturamottaker = orgnr.
Conflicting values within an import are recorded and become the blocking finding
`MOTSTRIDENDE_KUNDEDATA`. A missing kundenummer stays the blocking `MANGLER_KUNDENUMMER`. The
create-customers button, the deviation warnings and the hidden customer page go. The invoice gets its
own orgnr column (V17), and the registry tables are dropped in a separate V18. Old runs keep everything
through their snapshots, and the three retired control codes still render.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.x, Maven

**Primary Dependencies**: existing only (Spring MVC, Spring Data JDBC, Flyway, Thymeleaf/htmx). This
removes a dependency: the BRREG HTTP lookup.

**Storage**: PostgreSQL 16. `V17__kundedata_fra_dwh.sql` (additive: new table, backfills) and
`V18__dropp_kunderegister.sql` (FK/column drop, three tables dropped) ([data-model.md](data-model.md)).

**Testing**: JUnit 5 + Testcontainers (`mvn verify`); `BillingFixture` rewritten around
`bruksdata_kunde`; a migration IT from V16; LG04 golden files unchanged.

**Target Platform**: Linux container (docker compose locally, Azure Container Apps)

**Project Type**: single Spring Boot web application (server-rendered)

**Performance Goals**: generation no slower than today. One query per run for `bruksdata_kunde`
replaces three full-table loads.

**Constraints**: the migration is irreversible for the registry data → `pg_dump` before tt02 deploy
(quickstart). It must land before the December 2026 dry-run.

**Scale/Scope**: ~260 orgnr per import; touches `usage`, `billing`, `export`, `web`, `registry`
(deleted), templates, ~10 help articles, 6 docs.

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design. Still passes.*

| Principle | Status |
|---|---|
| I Never invent business values | Pass, strengthened. Conflicting DWH values are blocked, never picked (D2). Names are shown as delivered. No placeholder kundenummer. The dropped capabilities go to OQ-28 instead of being guessed. |
| II LG04 contractual | Pass. Writer untouched. Golden files must stay byte-identical; only fixture setup changes (R6). |
| III Traceability structural | Pass. `bruksdata_kunde` ties customer values to the import and its archived file. Invoices keep snapshots, backfilled where they relied on live reads (D5). Registry history stays in `hendelseslogg`. |
| IV Explicit billing period | Pass. Values come from the run's import (`fakturakjoring.bruksdata_import_id`), never «latest». |
| V Ports/portability | Pass. BRREG adapter removed; no new external calls. |
| VI Secrets from config | N/A (BRREG config keys removed). |
| VII Humans approve money | Pass. Removes a human write path; approval unchanged. Blocking findings still stop approval. |
| Workflow: help in same PR | Planned: learning center + control texts (US3, FR-014). |

No violations, so no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/018-fjern-kunderegister/
├── plan.md
├── research.md                 # R1–R6, decisions D1–D6
├── data-model.md               # V17
├── quickstart.md
├── contracts/ui-and-import.md  # removed routes, preview, CSV columns, API fields
├── checklists/requirements.md
└── tasks.md                    # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/resources/db/migration/V17__kundedata_fra_dwh.sql          # NEW (additive)
src/main/resources/db/migration/V18__dropp_kunderegister.sql        # NEW (drops)
src/main/java/no/digdir/forsystem/
├── usage/
│   ├── BruksdataKunde.java, BruksdataKundeRepository.java          # NEW
│   ├── Kundedata.java (+ collector)                                # NEW, replaces DwhCsv.KundeIFil
│   ├── DwhRad.java, DwhRespons.java, DwhCsv.java                   # CHANGED: name/bestillingsnummer, collect conflicts
│   ├── CsvUploadUsageSource.java, RaaBruksrad.java                 # CHANGED: optional customer columns (D3)
│   ├── Forhaandsvisning.java, UsageImportService.java              # CHANGED: carry + persist kunder
│   └── DwhImportService.java                                       # CHANGED: drop manglende/opprett/kryssjekk
├── billing/
│   ├── FakturakjoringService.java                                  # CHANGED: bygg() from bruksdata_kunde; live lookups gone
│   ├── Faktura.java, FakturaUnderTerskel.java                      # CHANGED: orgnr instead of kundeId
│   └── Kontrollkode.java                                           # CHANGED: + MOTSTRIDENDE_KUNDEDATA, 3 retired
├── export/ (EksportService, FakturagrunnlagRegneark)               # CHANGED: no registry reads
├── web/ BruksdataController, Fakturautvalg                         # CHANGED; KundeController DELETED
├── registry/ Kunde*, Avtalestatus, KundeService, Brreg*            # DELETED
└── demo/Demodata.java                                              # CHANGED: customer values on demo usage
src/main/resources/templates/{bruksdata/forhandsvis,bruksdata/liste,kjoring/kontroller}.html  # CHANGED
src/main/resources/templates/kunde/                                 # DELETED
src/main/resources/hjelp/                                           # CHANGED: Kunder section gone, texts → CRM
src/test/java/…                                                     # BillingFixture, import ITs, migration IT
docs/{01-context,03-datamodel,06-future,07-open-questions,09-beslutningslogg,funksjonsoversikt,csv-format,08-traceability}.md
```

**Structure Decision**: existing single-module layout. The `registry` package keeps products and
prices only.

## Delivery order (input to /speckit-tasks)

1. **V17 + `bruksdata_kunde` + collectors** (D1–D4). Import stores customer values; preview shows
   conflicts and missing kundenummer. Registry still in place.
2. **Generation from `bruksdata_kunde`** (R1, D6), `BillingFixture` rewrite, live lookups to invoice
   columns. Golden files green.
3. **Remove** the button, warnings, customer page, registry code, BRREG, demo seeding (US1).
4. **Help and docs** (US3): learning center, control texts, OQ-5 answered, OQ-28 added, decision log
   entry superseding B-14, `funksjonsoversikt` F-REG-KUN rows removed.

Steps 1–2 must ship together with 3. One PR, several commits are fine.

## Open items carried into tasks

- **D3 (own CSV with customer columns)**: confirm with Økonomi before step 1 is merged. The fallback
  is «own CSV format for tests/demo only».
- **OQ-28** (new): product-specific references, several kundenummer per orgnr
  (servicekode/tilleggstekst), avvikende fakturamottaker. If Økonomi needs them, CRM/DWH must deliver
  them.
- Check that the registry data in tt02 has no customer with several kundenummer rules or
  product-specific references that a January 2027 invoice would rely on. If there is one, raise it
  under OQ-28 before deploying.

## Complexity Tracking

None.
