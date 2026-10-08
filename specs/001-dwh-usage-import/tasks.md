---
description: "Tasks: Usage import from the datavarehus API"
---

# Tasks: Usage import from the datavarehus API

**Input**: `specs/001-dwh-usage-import/` (plan.md, spec.md, research.md, data-model.md, contracts/dwh-api.md)

**Tests**: Included — the constitution requires `mvn verify` green, and SC-002/SC-003 are test-defined.
Fixtures use fake organisation numbers only.

## Format: `[ID] [P?] [Story] Description`

Base package `src/main/java/no/digdir/forsystem/` is abbreviated `main/`; tests `src/test/java/no/digdir/forsystem/` as `test/`.

---

## Phase 1: Setup

- [x] T001 Add `forsystem.dwh.*` properties (enabled=false, base-url, entity, max-rows, auth, scope, timeouts, schedule, warn-deviation-percent) with a `@ConfigurationProperties` record `main/usage/DwhEgenskaper.java` and defaults in `src/main/resources/application.yaml`
- [x] T002 [P] Create fake-orgnr DAB fixtures in `src/test/resources/dwh/`: `september-ok.json`, `med-nextlink.json`, `utenfor-periode.json`, `tom.json`, `ukjent-produkt.json`, `ugyldig.json`

## Phase 2: Foundational (blocks all stories)

- [x] T003 Write `src/main/resources/db/migration/V7__dwh_bruksdata.sql`: table `produkt_kildenavn`; columns `raadata_url`, `raadata_sha256`, `hentet_at` on `bruksdata_import` + DWH check constraint (data-model.md)
- [x] T004 [P] Add `ProduktKildenavn` record and `ProduktKildenavnRepository` in `main/registry/`
- [x] T005 [P] Extend `main/usage/BruksdataImport.java` with `raadataUrl`, `raadataSha256`, `hentetAt`; update all constructors/call sites (UsageImportService, Demodata)
- [x] T006 [P] Define port `main/usage/DwhUsageClient.java` and records `DwhSvar` (rader, raadata bytes, hentetAt) and `DwhRad` in `main/usage/`
- [x] T007 Extract the row validation in `main/usage/UsageImportService.java` into a source-neutral method taking `List<RaaBruksrad>` and returning `Forhaandsvisning`; CSV path calls it unchanged (existing `UsageImportServiceIT` stays green)
- [x] T008 Generalise `RaaBruksrad.linjenr`/`AvvistRad` to a display reference (line number for CSV, `orgnr/produktnavn` for DWH) and update `templates/bruksdata/forhandsvis.html`

**Checkpoint**: migration applies, CSV import unchanged, `mvn verify` green.

## Phase 3: US1 — Fetch a period from the DWH (P1) 🎯 MVP

- [x] T009 [P] [US1] `test/usage/DwhAggregeringTest.java`: daily→monthly sums, several names→one product/type summed, unmapped name → one reject per (orgnr,name), out-of-period row rejected, amount on volume mapping rejected
- [x] T010 [P] [US1] `test/usage/DwhUsageClientContractTest.java` (MockRestServiceServer): request URL has the exact `$filter`/`$first` from contracts/dwh-api.md; parses `september-ok.json`
- [x] T011 [US1] Implement `main/usage/DwhAggregering.java` (mapping lookup from `ProduktKildenavnRepository`, aggregation, rejects) producing `RaaBruksrad`s
- [x] T012 [US1] Implement `main/usage/adapter/DabUsageClient.java` with Spring `RestClient`, configured timeouts, bean only when `forsystem.dwh.enabled=true`
- [x] T013 [US1] Add `UsageImportService.forhaandsvisFraDwh(Periode)` → `Forhaandsvisning` with `kilde=DWH` and raw payload attached; `importer(...)` stores `kilde` from the preview instead of hard-coded `Kilde.CSV`
- [x] T014 [US1] `POST /bruksdata/hent-dwh` in `main/web/BruksdataController.java` (FORVALTER); "Hent fra datavarehus" button in `templates/bruksdata/liste.html`, hidden when DWH disabled
- [x] T015 [US1] Extend `test/usage/UsageImportServiceIT.java`: fetch→preview→confirm stores `kilde='DWH'`, sums equal fixture totals exactly (SC-002), replaces previous import, honours the non-forkastet-run blocker

## Phase 4: US2 — Completeness and explainability (P1)

- [x] T016 [P] [US2] Contract tests in `DwhUsageClientContractTest`: `nextLink` present ⇒ `Regelbrudd("ufullstendig svar fra datavarehus")`; non-200, non-JSON, missing required field ⇒ failure; empty `value` ⇒ "ingen bruksdata i datavarehus for <periode> ennå"
- [x] T017 [US2] Implement the guards in `DabUsageClient` (never follow `nextLink`; refuse non-https base URL outside `local` profile)
- [x] T018 [US2] On confirm, archive raw payload via `FileArchive` at `bruksdata/<periode>/dwh-<timestamp>.json`; persist url/sha256/hentetAt; include sha256 in the `IMPORTERTE` audit event
- [x] T019 [US2] `GET /bruksdata/{id}/raadata` (LESER) and show source, fetch time and checksum in `templates/bruksdata/detalj.html`
- [x] T020 [US2] IT: confirmed DWH import has archived payload whose sha256 matches; download returns identical bytes (SC-004)

## Phase 5: US3 — Product-name mapping maintenance (P2)

- [x] T021 [P] [US3] `test/web/ProduktKildenavnControllerTest` (or IT): FORVALTER can add/edit/delete; LESER read-only; duplicate `kildenavn` rejected; changes audit-logged
- [x] T022 [US3] Service + `GET/POST /produkter/kildenavn` in `main/web/ProduktController.java` and `templates/produkter/kildenavn.html`; audit with entity `PRODUKT_KILDENAVN`
- [x] T023 [US3] Preview warnings (FR-007): add `advarsler` to `Forhaandsvisning`; registry cross-check of `kundenummer`/`fakturareferanse` per orgnr; per-product deviation vs previous `VALIDERT` import; render in `forhandsvis.html` (non-blocking) + unit tests

## Phase 6: US4 — Scheduled staging (P3)

- [x] T024 [US4] `UsageImportService.stageFraDwh(Periode)`: archive payload, create `MOTTATT` import (no rows), mark older `MOTTATT` for the period `AVVIST`; no-op if a `VALIDERT` import exists
- [x] T025 [US4] `bekreftStaget(id)`: load archived payload, re-run aggregation + validation, then commit like `importer(...)`; `POST /bruksdata/{id}/bekreft` + "Bekreft" on staged imports in `liste.html`/`detalj.html`
- [x] T026 [US4] `main/usage/DwhImportScheduler.java`: `@Scheduled(cron = forsystem.dwh.schedule.cron)`, `@ConditionalOnProperty(schedule.enabled)`; computes previous month from the trigger instant and passes it explicitly; logs and swallows DWH failures
- [ ] T027 *(dropped: one replica and the in-app scheduler suffice; an external trigger would need a machine-auth path the app does not have)* [US4] `POST /api/bruksdata/dwh/{periode}/stage` for an external k8s CronJob alternative (FORVALTER role)
- [x] T028 [US4] Notify FORVALTER that a staged import awaits (log + front-page banner for now; `Notifier` port when it exists)
- [x] T029 [US4] IT: stage → no `bruksdata` rows; confirm → rows + `VALIDERT`; scheduler with existing `VALIDERT` does nothing; DWH down leaves data untouched

## Phase 7: Auth & deployment (FR-011, parallel to any phase once OQ-19a lands)

- [x] T030 [P] `TokenProvider` port in `main/usage/`; `main/usage/adapter/EntraTokenProvider.java` using `azure-identity` `DefaultAzureCredential` with configured scope; `none` implementation for local/tests
- [x] T031 *(enabled in tt02 + prod via base deployment, auth none until OQ-19a; egress to be confirmed on first use)* [P] Deploy: `DWH_BASE_URL`, `FORSYSTEM_DWH_ENABLED`, `DWH_SCOPE` in `syncroot/` overlays (tt02 first); confirm egress to `*.azurecontainerapps.io`

## Phase 8: Polish

- [x] T032 [P] Docs: `docs/csv-format.md` (CSV is fallback), `docs/06-future.md` §1 (done/remaining), `docs/funksjonsoversikt.md` (new F-USG rows), `docs/brukerveiledning.md` §7, `docs/runbook.md` (monthly flow from the 7th), `docs/03-datamodel.md` (V7)
- [x] T033 Run quickstart.md §1–2; `./mvnw verify` green; one conventional commit per slice

## Dependencies & order

- Phase 1 → Phase 2 → US1 → US2 (US2 guards are needed before any real use) → US3 → US4.
- T009/T010 before T011/T012 (tests first). T030/T031 independent; required only for prod.
- **MVP = Phases 1–4 + T022** (mapping UI needed to make any row valid) — target before the December 2026 dry-run.
