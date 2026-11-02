# Requirements Checklist: Versioned correction runs

**Purpose**: Validate the specification and plan before implementation
**Created**: 2026-11-02
**Feature**: [spec.md](../spec.md)

## Product definition

- [x] CHK001 Distinguishes pre-export replace/regenerate from post-export correction.
- [x] CHK002 Defines V2 as selected signed differences, not the new full values.
- [x] CHK003 Covers added, changed, removed and unchanged keys.
- [x] CHK004 Defines selection granularity and unchecked default.
- [x] CHK005 Defines what happens to unselected and explicitly dismissed changes.
- [x] CHK006 Defines V3+ baseline semantics after partial V2 selection.
- [x] CHK007 States native forsystem CSV correction is out of first-increment scope for completeness safety.

## Financial safety and governance

- [x] CHK008 Preserves immutable V1 and all source payloads/rows.
- [x] CHK009 Keeps explicit human selection, reason, approval and export roles.
- [x] CHK010 Uses V1 price version for usage correction.
- [x] CHK011 Treats negative delta as a domain requirement without inventing Unit4 semantics.
- [x] CHK012 Makes OQ-6/OQ-23 and tt02 evidence a hard production-export gate.
- [x] CHK013 Requires unique visible version/file/batch identity.
- [x] CHK014 Keeps test reset explicitly outside the production correction flow.

## Traceability and data integrity

- [x] CHK015 Links correction line → selected change → old/new state → candidate archive → run chain.
- [x] CHK016 Defines old + delta = new reconciliation per key and in aggregate.
- [x] CHK017 Defines effective invoiced basis from exported selections only.
- [x] CHK018 Handles stale comparisons, duplicate payloads, supersession and concurrent generation.
- [x] CHK019 Versions discarded attempts without silently reusing identifiers.
- [x] CHK020 Leaves existing ordinary import/run/reconciliation semantics explicit.

## UX and operability

- [x] CHK021 Specifies comparison summary, table columns, filters, paging and accessibility.
- [x] CHK022 Specifies no-change, invalid, stale, superseded and in-progress states.
- [x] CHK023 Labels run pages/list/files as V1/V2+ and ordinary/correction.
- [x] CHK024 Provides measurable user-time and performance outcomes.
- [x] CHK025 Includes local walkthrough, automated suites and tt02 acceptance gate.

## Architecture and delivery

- [x] CHK026 Constitution principles I–VII are evaluated explicitly.
- [x] CHK027 Schema changes, state transitions, constraints and indexes are documented.
- [x] CHK028 Source paths and transaction boundaries are identified.
- [x] CHK029 Tasks are ordered by independently testable user story and include exact paths.
- [x] CHK030 Ordinary LG04 byte identity is a named regression invariant.

## Review items requiring humans

- [ ] CHK031 Økonomi + VIS confirm OQ-6 correction/credit mechanism.
- [ ] CHK032 FEL/VIS confirm correction batch/version format under OQ-6/OQ-23.
- [ ] CHK033 FORVALTER users confirm unchecked default and dismissal wording.
- [ ] CHK034 VIS completes representative tt02 import and total reconciliation before production enablement.

## Notes

- CHK031–CHK034 are implementation/delivery gates, not missing architectural placeholders.
- If any human decision changes the domain behavior (rather than export encoding or wording), revise
  `spec.md`, `research.md`, `data-model.md` and affected tasks before implementation continues.
