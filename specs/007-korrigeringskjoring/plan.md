# Implementation Plan: Versioned correction runs

**Branch**: `codex/007-korrigeringskjoring` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-korrigeringskjoring/spec.md`

## Summary

Add a non-destructive post-export correction workflow. A complete new DWH snapshot is archived as a
candidate, normalized once and compared with the effective invoiced basis (V1 plus exported selected
corrections). Users review only changed keys, select a subset and generate a versioned correction run
whose invoice lines contain signed deltas only. Existing ordinary runs and LG04 bytes remain unchanged.

The domain, schema, UI, audit and test work can be implemented from this plan. Production export of
negative corrections has a hard business gate: OQ-6/OQ-23 must document the confirmed Unit4 mechanism
and a representative tt02 import must pass.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx,
Digdir Designsystemet, Apache POI/OpenHTMLtoPDF through existing export modules

**Storage**: PostgreSQL 16 with Flyway; local/Azure `FileArchive` port for immutable raw and export files

**Testing**: JUnit 5, AssertJ, Spring Boot integration tests, MockMvc, Testcontainers PostgreSQL,
existing LG04 golden-file and reader/writer reconciliation tests

**Target Platform**: Linux container in dis-core; fully functional local Docker Compose

**Project Type**: Server-rendered web application with REST-adjacent MVC endpoints

**Performance Goals**: Compare 10 000 normalized keys in <10 s; page/filter 100 changes without
loading the full result into the HTTP session; correction generation remains within existing run scale

**Constraints**: No external services in `mvn verify`; source snapshots non-negative; history immutable;
explicit period; human selection/approval; ordinary LG04 output byte-identical; no accounting guesses

**Scale/Scope**: Monthly cadence, thousands to low tens of thousands of usage keys, a few hundred
invoices, normally 1–3 versions per corrected month

## Constitution Check

*Gate before Phase 0: PASS with two explicit external decision gates.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. OQ-6/OQ-23 own credit mechanism and batch format. Negative export stays disabled until answered/tested. |
| II. LG04 Output Is Contractual | PASS. Ordinary-run path is untouched and golden-tested. Correction export adds tests and cannot change shared offsets silently. |
| III. Traceability Is Structural | PASS. Candidate raw payload, normalized rows, diff, selection, run, line and previous version are linked; nothing is rewritten. |
| IV. Explicit Billing Period | PASS. Candidate, comparison and run all carry the explicit first-of-month period. |
| V. Portability Behind Ports | PASS. DWH and archive reuse existing ports; local fixtures cover the flow. |
| VI. Secrets From Configuration Only | PASS. No new credential path. |
| VII. Humans Approve Money | PASS. No checkbox is preselected; generation requires reason; existing GODKJENNER approval/export remains. |

*Post-design re-check: PASS.* The design adds schema and domain types but no new deployable or external
dependency. Complexity is justified by the immutable correction chain; a mutable "replace V1" model
would violate principles III and VII.

## Architecture

```text
Complete DWH snapshot
        │ validate + archive (immutable)
        ▼
Candidate import + normalized rows
        │ compare by (orgnr, product, type)
        ▼
Comparison against effective invoiced basis
        │ user selects explicit change IDs + reason
        ▼
Versioned correction run (V2+)
        │ existing controls → approval → export
        ▼
Selected deltas become effective only when EKSPORTERT
```

The effective basis is derived from V1 and exported correction selections, not stored as a mutable
replacement. This is the central invariant: a candidate says what the DWH says now; a correction run
says which differences humans actually chose to invoice.

### Module responsibilities

- `usage`: candidate persistence, effective-basis assembly, diff/classification, comparison lifecycle,
  selection validation and dismissal.
- `billing`: run version allocation, correction generation, reuse of V1 price/billing snapshots,
  correction controls and signed reconciliation.
- `export`: versioned display/file identity and signed totals. Unit4 negative representation remains
  behind a feature gate until OQ-6 is resolved.
- `web`: candidate routing, comparison filters/checkbox form, V1/V2 labels and traceability links.

### Transaction boundaries

- Candidate creation archives raw bytes first using the existing archive behavior, then stores
  candidate/import rows/comparison atomically. Retry is idempotent by checksum + baseline.
- Correction generation is one DB transaction with a per-period lock and stale-baseline check.
- Export retains the existing build → read-back → reconcile → archive → status pattern.

## Project Structure

### Documentation (this feature)

```text
specs/007-korrigeringskjoring/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
├── contracts/
│   └── ui.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/
├── java/no/digdir/forsystem/
│   ├── usage/
│   │   ├── BruksdataKandidatService.java        # stage complete immutable candidate
│   │   ├── BruksdataSammenligningService.java   # effective basis, diff, paging, dismissal
│   │   ├── BruksdataSammenligning.java
│   │   ├── BruksdataEndring.java
│   │   └── *Repository.java
│   ├── billing/
│   │   ├── Fakturakjoring.java                  # type/version/previous/comparison/reason
│   │   ├── FakturakjoringService.java           # ordinary path unchanged; correction entry point
│   │   ├── KorrigeringskjoringService.java       # selection + delta generation
│   │   └── KorrigeringsavstemmingService.java
│   ├── export/
│   │   ├── EksportService.java                  # version identity, signed correction gate
│   │   └── FakturagrunnlagRegneark.java          # change provenance columns for corrections
│   └── web/
│       ├── BruksdataController.java             # route closed-period DWH input to candidate flow
│       ├── BruksdataSammenligningController.java
│       └── FakturakjoringController.java         # version/change-chain model attributes
├── resources/
│   ├── db/migration/V12__korrigeringskjoring.sql
│   └── templates/
│       ├── bruksdata/sammenligning.html
│       └── kjoring/{liste,fragmenter,oversikt,kontroller,filer,historikk}.html

src/test/java/no/digdir/forsystem/
├── usage/{BruksdataSammenligningTest,BruksdataKandidatServiceIT}.java
├── billing/{KorrigeringskjoringIT,KorrigeringsavstemmingIT,KjoringVersjonIT}.java
├── export/{KorrigeringEksportIT,Lg04KorrigeringTest}.java
└── web/{BruksdataSammenligningWebIT,KjoringVersjonWebIT}.java
```

**Structure Decision**: Keep the existing single Spring Boot application and package boundaries. A
dedicated correction service avoids destabilizing the ordinary generator while reusing its shared
lookup/control helpers through small extracted functions.

## Delivery Phases

### Phase 0 — Resolve delivery gates and pin fixtures

Record OQ-6/OQ-23 answers, obtain an anonymized complete old/new DWH fixture pair, and agree dismissal
wording with FORVALTER users. Draft/domain work may start, but negative production export may not.

### Phase 1 — Immutable comparison (US1)

Add V12 candidate/comparison/change schema, effective-basis computation, exact diff tests, idempotent
candidate flow and paged comparison UI. No billing behavior changes yet.

### Phase 2 — Correction generation (US2)

Add versioned run metadata, replace the partial uniqueness rule, persist selection, generate signed
delta lines using V1 prices/snapshots, and add correction reconciliation. Keep exports gated.

### Phase 3 — Repeatability and audit (US3/US4)

Add recomparison against V2+, dismissal/supersession/stale protections, run-chain UI, filenames and
audit views.

### Phase 4 — Confirmed export mechanism

Implement the documented OQ-6 adapter behavior, signed spreadsheet/PDF/LG04 reconciliation, unique
confirmed batch identity, tt02 test import and production flag enablement.

### Phase 5 — Documentation and regression

Update data model, user guide, runbook, feature inventory, traceability, decision log and OQ-6/OQ-23.
Run full `./mvnw verify`; explicitly compare an ordinary V1 LG04 golden output before/after.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Negative LG04 accepted mechanically but rejected/accounted wrongly | OQ-6 hard gate + tt02 import with report reconciliation |
| User mistakes a full new value for a delta | UI always shows old/new/difference; run and exports label correction and signed difference |
| Partial selection loses the remaining changes | Effective basis applies exported selections only; recomparison resurfaces the rest |
| Two tabs generate the same correction | Per-period lock, OPEN status check, baseline token and atomic selection/run transaction |
| Product mapping changed | Persist normalized candidate and classify as remove+add; never infer rename |
| Registry/price drift changes more than usage | Root V1 price; reuse root billing snapshots; current lookup only for genuinely new keys |
| Migration breaks existing run uniqueness | Backfill/invariant IT with multiple historical FORKASTET runs and one active/exported run |
| Comparison list is too large | Persist/index changes, server paging/filtering, no session-sized diff |

## Complexity Tracking

| Added complexity | Why needed | Simpler alternative rejected because |
|---|---|---|
| Comparison and change tables | Freeze old/new/delta, selection and audit | Recomputing at view time changes when mappings/history change |
| Versioned run chain | Multiple immutable deliveries for one month | Reopening/replacing exported V1 rewrites history |
| Derived effective basis | Preserve partial selections across V2/V3 | Treating candidate as the new baseline makes unselected changes disappear |
| Separate correction source on invoice line | Trace signed delta to both source states | Pointing only at new usage cannot explain reductions/removals |

