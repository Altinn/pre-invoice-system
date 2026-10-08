# Implementation Plan: Usage import from the datavarehus API

**Branch**: `feat/dwh-usage-api` | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-dwh-usage-import/spec.md`

## Summary

Add a second usage source next to CSV: a REST client for the DWH's Data API Builder view
`mv_altinn_usage_monthly`. It fetches one period in a single request, refuses anything that might be
incomplete, aggregates daily rows to monthly per (orgnr, product, type) via a maintained
product-name mapping, and hands `RaaBruksrad`s to the **existing** validation → preview → confirm
pipeline. The raw response is archived with SHA-256 for traceability. A scheduled job (off by
default) stages the previous month on the 7th for a FORVALTER to confirm. See
[research.md](./research.md) for the API findings and decisions D1–D8.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x (web `RestClient`, data-jdbc, security, scheduling),
Flyway, Thymeleaf + htmx. Entra tokens via a plain OAuth2 workload-identity exchange in the adapter —
no Azure SDK on the compile classpath

**Storage**: PostgreSQL 16 (one migration, V7); raw payloads via `FileArchive`

**Testing**: JUnit 5, `MockRestServiceServer` for the HTTP contract, Testcontainers PostgreSQL ITs

**Target Platform**: Linux container on AKS (dis-core) with Linkerd; local `docker compose`

**Project Type**: Server-rendered web application (single Maven module)

**Performance Goals**: One fetch per month; ~2.5k source rows today, design for ≤ 100k rows in one
response (< 30 s end-to-end)

**Constraints**: Must not under-count (refuse on doubt); no real customer data in the repo; DWH
access is optional at build and local runtime

**Scale/Scope**: ~60 organisations × ~8 product names × 31 days per month

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | Product mapping is a table with no seed rows (OQ-18); unmapped names reject |
| II. LG04 contractual | ✅ | Not touched; generation reads the same monthly `bruksdata` |
| III. Traceability | ✅ | Raw payload archived + sha256 on the import; line → bruksdata → import → payload |
| IV. Explicit period | ✅ | Fetch takes `Periode`; the scheduler computes "previous month" from its trigger time and passes it explicitly |
| V. Portability | ✅ | `DwhUsageClient` port; Entra token provider in `usage.adapter`; disabled by default locally/tests |
| VI. Secrets from config | ✅ | Base URL/scope from env; token via workload identity, no secrets in code |
| VII. Humans approve money | ✅ | Scheduler only stages (`MOTTATT`); confirm is a FORVALTER action, audited |

Post-design re-check: unchanged, all ✅. No complexity-tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/001-dwh-usage-import/
├── spec.md
├── plan.md              # this file
├── research.md          # API findings, defects R1–R5, decisions D1–D8, new OQs
├── data-model.md        # V7 migration, state transitions, aggregation, warnings
├── quickstart.md
├── contracts/dwh-api.md # consumed DAB contract, exposed endpoints, config
└── tasks.md             # /speckit-tasks
```

### Source Code

```text
src/main/java/no/digdir/forsystem/
├── usage/
│   ├── DwhUsageClient.java            # port: hentMaaned(Periode) → DwhSvar (raw bytes + hentetAt)
│   ├── DwhRespons.java, DwhRad.java   # strict DAB parser; nextLink ⇒ fail
│   ├── DwhAggregering.java            # daily → monthly RaaBruksrad, mapping, rejects
│   ├── DwhImportService.java          # fetch, registry cross-check, stage, confirm staged
│   ├── DwhEgenskaper.java             # forsystem.dwh.* properties
│   ├── UsageImportService.java        # shared vurder() pipeline; importer() archives DWH payload
│   ├── Forhaandsvisning.java, Raadata.java  # + kilde, advarsler, raadata, stagetImportId
│   ├── BruksdataImport.java           # + raadataUrl, raadataSha256, hentetAt
│   ├── DwhImportScheduler.java        # @Scheduled, conditional on forsystem.dwh.planlegging.enabled
│   └── adapter/
│       ├── DabUsageClient.java        # RestClient impl, one request per period
│       ├── WorkloadIdentityTokenProvider.java  # client-credentials with federated token, no SDK
│       └── DwhKonfigurasjon.java      # wires the adapter only when forsystem.dwh.enabled
├── registry/
│   ├── ProduktKildenavn.java, ProduktKildenavnRepository.java, ProduktKildenavnService.java
├── web/
│   ├── BruksdataController.java       # + hent-dwh, raadata, bekreft staged
│   └── ProduktController.java         # + kildenavn maintenance
src/main/resources/
├── db/migration/V7__dwh_bruksdata.sql
├── templates/bruksdata/*.html         # DWH button, warnings, raw-data link
└── templates/produkter/kildenavn.html
src/test/java/no/digdir/forsystem/usage/
├── DwhUsageClientContractTest.java
├── DwhAggregeringTest.java
└── UsageImportServiceIT.java          # extended
src/test/resources/dwh/*.json          # fake-orgnr fixtures
```

**Structure Decision**: Stay inside the existing `usage` module; the HTTP adapter sits in
`usage.adapter` per docs/02's portability rule. The mapping lives in `registry` because it is
reference data maintained alongside products.

## Delivery slices

1. **Slice A (US1+US2, P1)** — mapping table + UI, DAB client with completeness guard, aggregation,
   manual "Hent fra datavarehus", raw-payload archiving. Usable with `auth: none` against today's
   endpoint in tt02 for a dry run. **Target: before the December 2026 dry-run.**
2. **Slice B (US3 polish + FR-007)** — preview warnings (registry cross-check, month-over-month).
3. **Slice C (US4, P3)** — persisted `MOTTATT` staging, scheduler, notification, `/api/.../stage`.
   Enable after 2–3 clean manual months (earliest March 2027).

Entra auth (FR-011) lands whenever OQ-19a does; until then the client runs with `auth: none` and we
should press the DWH team to close the endpoint.

## Dependencies / blockers

- **OQ-18** product-name mapping must be confirmed before production use (not before building).
- **OQ-19** DAB fixes: auth (security), key-fields (removes the single-request limit), test endpoint.
- **OQ-20** retention/refresh semantics — affects whether re-fetching a past month is possible.
- **OQ-21** quantity units per product — affects whether pricing multiplies the right number.
- Network: egress from the AKS namespace to `*.azurecontainerapps.io` (no egress policy today;
  confirm cluster firewall).

## Complexity Tracking

None.
