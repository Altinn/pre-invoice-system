# Implementation Plan: Import the datavarehus export with invoice flags

**Branch**: `017-dwh-flagg-import` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-dwh-flagg-import/spec.md`

## Summary

Make the existing «upload the datavarehus CSV» path understand the flagged 3-month export. The reader
classifies every row (used / outside period / DPV / excluded) from `flag_inv_altinn`, `flag_inv_dpv` and
`regel` *before* any validation, so rows that were never billable can no longer break or pollute the import;
only used rows reach the existing aggregation, mapping and all-or-nothing pipeline. The preview gets a
«left out and why» breakdown, and each committed import stores a grouped source summary
(`bruksdata_kilde`) for traceability. Files without the flag columns follow the old code path unchanged.
No new upload route: the header already routes `organisation_number` files to the DWH reader.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.x, Maven (as CLAUDE.md)

**Primary Dependencies**: existing only — Spring MVC multipart, Spring Data JDBC, Flyway, Thymeleaf/htmx. No CSV library is added (the project has its own RFC 4180 reader in `DwhCsv`).

**Storage**: PostgreSQL 16; one new table via Flyway `V16__bruksdata_kilde.sql`; raw file archived via `FileArchive` as today

**Testing**: JUnit 5 + Testcontainers (`mvn verify`); unit tests on the reader/classifier, an IT through the controller path (`DwhFilImportIT`-style), fixture under `src/test/resources/dwh/`

**Target Platform**: Linux container (docker compose locally, Azure Container Apps in prod)

**Project Type**: single Spring Boot web application (server-rendered)

**Performance Goals**: 30 MB / 113 k rows: upload + preview < 30 s (SC-004)

**Constraints**: Heap is not pinned in the Dockerfile; a 30 MB upload is currently held as `byte[]` in the HTTP session. Spring's default multipart limit is 1 MB and no override is configured, so a 30 MB upload is refused today (research R1).

**Scale/Scope**: up to ~55 k day rows per month, 3 months per file, ~260 organisations, 8 product names

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design — still passes.*

| Principle | Status |
|---|---|
| I Never invent business values | Pass. No prices, mappings or accounts added. `regel` tokens are displayed, not interpreted; DPV/flag semantics go to OQ-25/26/27. |
| II LG04 contractual | Pass. LG04 untouched. |
| III Traceability structural | Pass, improved: `bruksdata_kilde` per import + archived file with checksum; superseded imports keep their groups (rows are never deleted). |
| IV Explicit billing period | Pass. Period stays the upload parameter; month filtering uses it, never the clock. |
| V Ports/portability | Pass. No Azure imports; all in `usage` + `web`. |
| VI Secrets from config | N/A. |
| Tests / docs per phase | Planned: golden fixture, regression on both old fixtures, docs updates (FR-014). |

No violations, so no Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/017-dwh-flagg-import/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── dwh-flag-export.md      # input file contract (as understood; OQ-25)
│   └── upload-ui.md            # preview + drill-down screens
├── checklists/requirements.md
└── tasks.md                    # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/no/digdir/forsystem/usage/
├── DwhCsv.java                 # CHANGED: one streaming pass; reads new columns; lenient on unused rows
├── DwhRad.java                 # CHANGED: + flaggAltinn, flaggDpv, kildetabell, ressurs, regel (nullable)
├── DwhRadklasse.java           # NEW: enum BRUKT | UTENFOR_PERIODE | DPV | EKSKLUDERT (+ classifier)
├── DwhFilrapport.java          # NEW: counts + volume per class/product/reason, top excluded orgs
├── DwhKildegruppe.java         # NEW: record of the grouped source summary
├── DwhAggregering.java         # CHANGED: consumes classified rows, emits kildegrupper
├── DwhImportService.java       # CHANGED: forhaandsvisFil uses the one-pass result; customers from used rows only
├── Forhaandsvisning.java       # CHANGED: + filrapport, kildegrupper (nullable for old paths)
├── UsageImportService.java     # CHANGED: importer() persists kildegrupper
├── BruksdataKilde.java (+Repository)  # NEW: Spring Data JDBC entity for bruksdata_kilde
src/main/java/no/digdir/forsystem/web/BruksdataController.java   # CHANGED: detail view lists groups
src/main/resources/db/migration/V16__bruksdata_kilde.sql         # NEW
src/main/resources/application.yaml                              # CHANGED: multipart limits
src/main/resources/templates/bruksdata/{forhandsvis,detalj}.html # CHANGED
src/test/java/no/digdir/forsystem/usage/                         # NEW/CHANGED tests
src/test/resources/dwh/eksport-flagg-3m.csv                      # NEW reduced fixture
docs/{csv-format,07-open-questions,brukerveiledning,runbook,03-datamodel}.md  # CHANGED
```

**Structure Decision**: extend the existing `usage` package; no new module or route. The DWH JSON/API path
(`DwhRespons`) keeps producing `DwhRad`s with null flags, so it is classified BRUKT and behaves as before.

## Design outline

1. **Read** (`DwhCsv`): parse in a single streaming pass over the decoded text; per row build a `DwhRad`
   with the new nullable fields. If the header has `flag_inv_altinn` the file is *flagged*; otherwise legacy.
   In a flagged file `organisation_number`, `quantity` and `amount_nok` are parsed leniently (kept as text /
   null) and validated later only for BRUKT rows. `NULL`, empty, `unknown`, `owner` and names are just
   «ingen gyldig orgnr» on unused rows. `resource_id` is normalised (strip `urn:altinn:resource:`).
2. **Classify** (`DwhRadklasse`): legacy file → BRUKT. Flagged file → date outside period → UTENFOR_PERIODE
   (counted per month); else `flag_inv_altinn=1` → BRUKT; else `flag_inv_dpv=1` → DPV; else EKSKLUDERT with
   the reason text taken verbatim from the `ekskludert*` tokens of `regel` (or «ingen_positiv_mengde» label
   when volume is 0/NULL and `regel` has none). Flag values other than `0`/`1` → rejected row (US4).
   Date-outside-period check comes first so a flagged November row is «other month», never «used».
3. **Aggregate** (`DwhAggregering`): unchanged rules for BRUKT rows, plus: NULL quantity on a flagged-1
   volume row counts as 0 and zero rows add nothing; appinfra keeps `amount_nok`. Emits one
   `DwhKildegruppe` per (orgnr, produktkode, type, kildetabell, ressurs) with day-row count, sum and
   distinct `regel` texts; the groups sum to the usage row (asserted in tests).
4. **Report** (`DwhFilrapport`): totals so that used + other months + DPV + excluded = all rows (SC-003);
   per-product volume per class; excluded volume by orgnr (including «ukjent orgnr»).
5. **Gate**: flagged file with zero BRUKT rows in the period → blokkering with explanation (FR-012).
6. **Customers**: `manglendeKunder`/`opprettManglendeKunder` take organisations from BRUKT rows only.
7. **Commit**: `importer()` saves usage as today and writes `bruksdata_kilde` rows for the import.
   Superseded imports keep theirs (read-only).
8. **UI**: preview shows the report table and a collapsible list of top excluded organisations;
   import detail page shows groups per usage row. Event log entry gains `brukte`, `utelatt` counts.

## Risks

- **Upload size**: see R1/R2 — without the multipart limit change the feature cannot work at all.
- **Memory**: R2. Streaming + keeping only BRUKT rows keeps the footprint near the file size.
- **Semantics of flags are the DWH's, unconfirmed** (OQ-25): mitigated by showing everything left out.
