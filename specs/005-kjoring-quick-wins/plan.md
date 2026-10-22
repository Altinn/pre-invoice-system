# Implementation Plan: A run page that says what to do next

**Branch**: `005-kjoring-quick-wins` (stacked on `004-designsystemet-ux`, PR #29) | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-kjoring-quick-wins/spec.md`

## Summary

Restructure `kjoring/detalj.html` into this order:

1. title
2. stopped-export error
3. **next-step panel** (per status)
4. summary
5. **grouped findings**
6. **Filer**
7. invoices
8. LG04 reconciliation result

Support code:
- `Fakturakjoring.periodeLabel()` shows "januar 2027" in titles, breadcrumbs and the list.
- A small view record, `Funngruppe`, groups and orders the findings.
- The controller passes the groups, and the usage reconciliation for GENERERT runs, to the page.

There is no new route, migration or business rule. See [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21, Thymeleaf, CSS (spec 004 conventions)

**Primary Dependencies**: Spring Boot 3.5.x, Thymeleaf + htmx, Designsystemet 1.23. No new dependencies.

**Storage**: None. No schema change.

**Testing**:
- a JUnit unit test for `Funngruppe` (grouping and order)
- MockMvc ITs for the panel per status and for the grouped findings
- the existing ITs, updated only where they assert the old date label

**Target Platform**: AKS; local `docker compose`

**Project Type**: Server-rendered web application (single Maven module)

**Performance Goals**: The usage reconciliation is computed for GENERERT runs only. It is the same
query the reconciliation page already runs. Other statuses add no query.

**Constraints**:
- actions keep the spec 004 confirmation dialogs
- downloads keep `hx-boost="false"`
- `TemplateKonvensjonTest` still passes

**Scale/Scope**: About 300 invoices and up to a few hundred findings per run. One template, one
controller method and two small records.

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | No values. The checks reuse existing rules |
| II. LG04 contractual | ✅ | Not touched |
| III. Traceability | ✅ | No data change. Every finding is still listed, grouped not dropped |
| IV. Explicit period | ✅ | The regenerate shortcut posts the run's own period explicitly. Nothing derives it from today |
| V. Portability | ✅ | No new dependency |
| VI. Secrets | ✅ | None |
| VII. Humans approve money | ✅ | Godkjenn stays an explicit, confirmed action. Blocked while blocking findings exist |

Post-design re-check: all ✅.

## Project Structure

```text
specs/005-kjoring-quick-wins/   plan.md, research.md, data-model.md, contracts/ui.md, quickstart.md, tasks.md

src/main/java/no/digdir/forsystem/
├── billing/Fakturakjoring.java          + periodeLabel()
└── web/
    ├── Funngruppe.java                  NEW: findings grouped by code, ordered
    └── FakturakjoringController.java    detalj(): + funngrupper, bruksdataavstemming (GENERERT)
src/main/resources/
├── templates/kjoring/{detalj,liste,faktura,avstemming,lg04-kontroll}.html
└── static/css/forsystem.css             + .neste-steg panel, .sjekkliste
src/test/java/no/digdir/forsystem/
├── web/FunngruppeTest.java              NEW
└── billing/KjoringNesteStegIT.java      NEW (panel per status, grouped findings, Filer)
```

## Complexity Tracking

None.
