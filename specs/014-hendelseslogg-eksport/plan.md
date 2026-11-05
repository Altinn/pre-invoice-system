# Implementation Plan: The complete event log, filtered and exported

**Branch**: `claude/014-hendelseslogg-eksport` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

## Summary

`common.HendelsesloggSok` runs one filtered query (NamedParameterJdbcTemplate, dynamic `where`, `order by
tidspunkt desc, id desc`) for a page (`limit/offset` + `count(*)`) or for everything. `common.Hendelsesfilter`
holds the filters, turns the dates into Oslo-day bounds, and builds the query string for paging and
download links. `common.Detaljtekst` flattens the jsonb details to «nøkkel: verdi; …» and lifts out
`periode`. `export.HendelsesloggEksport` builds the CSV/XLSX through `RegnearkEksport` and logs the download.
`RegnearkEksport` learns date-time cells, an autofilter and a frozen header row (all XLSX downloads benefit).
The page gets a filter form, Oslo times, readable details, paging and download links.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring JDBC, Spring MVC, Thymeleaf + htmx, Designsystemet CSS,
Jackson, Apache POI (existing)

**Storage**: PostgreSQL 16 — no schema change (no index needed at a few thousand rows a year; avoids a
migration number clash with specs/013's V15)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers; LG04 golden file unchanged

**Target Platform**: Linux container; local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: A page is two indexed-by-pk-order queries; a full download is one query

**Constraints**: read-only except the download's own audit row; no invented values

**Scale/Scope**: Thousands of rows per year

## Constitution Check

*Gate before Phase 0: PASS. Re-checked after design: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. No business values; the details are rendered, not interpreted. |
| II. LG04 Output Is Contractual | PASS. Untouched; golden test unchanged. |
| III. Traceability Is Structural | PASS, strengthened: the whole audit trail becomes visible and exportable; rows are read, never edited. |
| IV. Explicit Billing Period | PASS. The date filter is a user-chosen range for reading the log, not a billing period; `periode` is copied from the details as recorded. |
| V. Portability Behind Ports | PASS. No new dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret. |
| VII. Humans Approve Money | PASS. Not affected. |

## Project Structure

```text
specs/014-hendelseslogg-eksport/ (spec, plan, research, data-model, quickstart, tasks, contracts/ui.md, checklists)

src/main/java/no/digdir/forsystem/
├── common/Hendelsesfilter.java        # NEW: filters, Oslo-day bounds, query string
├── common/HendelsesloggSok.java       # NEW: filtered page / all rows / distinct values
├── common/Detaljtekst.java            # NEW: jsonb → readable text, periode
├── export/HendelsesloggEksport.java   # NEW: CSV/XLSX + LASTET_NED
├── export/RegnearkEksport.java        # date-time cells, autofilter, frozen header
└── web/HendelsesloggController.java   # filters, paging, downloads
src/main/resources/templates/hendelseslogg/liste.html
src/test/java/no/digdir/forsystem/common/DetaljtekstTest.java      # NEW
src/test/java/no/digdir/forsystem/web/HendelsesloggIT.java         # NEW
docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/09-beslutningslogg.md (B-22)
```

**Structure Decision**: Query and rendering live in `common` next to `AuditService`; the file building lives
in `export` next to the other spreadsheet downloads.

## Complexity Tracking

No constitution violations to justify.
