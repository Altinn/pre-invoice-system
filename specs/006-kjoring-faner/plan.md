# Implementation Plan: Run page in tabs, with progress and invoice search

**Branch**: `006-kjoring-faner` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-kjoring-faner/spec.md`

## Summary

The run page is split into five tabs, each a server-rendered page with its own address, under
`/kjoringer/{id}`:

| Tab | Address |
|---|---|
| Oversikt | (default) |
| Kontroller | `/kontroller` |
| Fakturaer | `/fakturaer` |
| Filer | `/filer` |
| Historikk | `/historikk` |

A shared fragment renders the header and the tab bar. Three small view records in `web` carry the
new logic, and each is unit-tested:

- `Fremdrift`: the progress strip, from the run and its audit events.
- `Fakturautvalg`: invoice search, sort and paging, done in memory.
- `Kjoringsrad`: one row of the run list, with counts.

The audit repository gets one finder per entity. The old `/avstemming` address redirects to the
Kontroller tab. See [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21, Thymeleaf, CSS. No new JS of our own: paging uses
`<ds-pagination>` from the vendored designsystemet-web.

**Primary Dependencies**: Spring Boot 3.5.x, Thymeleaf + htmx, Designsystemet 1.23. Nothing new.

**Storage**: No schema change. One derived query, `HendelsesloggRepository.findByEntitetAndEntitetId…`.

**Testing**:
- JUnit unit tests for `Fremdrift` and `Fakturautvalg`.
- `KjoringFanerIT` covers the tabs, the badges, search and paging, the redirect and history.
- Existing ITs are updated where content moved to a tab:
  - `KjoringNesteStegIT`: the findings and Filer cases now open their tab.
  - `BruksdataavstemmingIT`: now requests `/kontroller`.

**Target Platform**: AKS; local `docker compose`

**Project Type**: Server-rendered web application

**Performance Goals**:
- Each tab loads the run, its invoices (a few hundred) and its findings, enough for the header
  counts.
- The usage reconciliation is computed only on Kontroller, and on Oversikt for GENERERT runs (005).
- The run list computes a summary per run, which is cheap at about 12 runs a year.

**Constraints**:
- actions stay on Oversikt with their dialogs
- `TemplateKonvensjonTest` still passes (fragment-only templates are exempt by name)
- no change to rules, roles or LG04

**Scale/Scope**:
- new templates: `kjoring/{fragmenter,kontroller,fakturaer,filer,historikk}.html`
- `kjoring/avstemming.html` folds into Kontroller
- 3 view records
- 1 repository method

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | No new values. Status texts are display labels |
| II. LG04 contractual | ✅ | Not touched |
| III. Traceability | ✅ | Improved: the run's own audit trail and who/when per step are shown on the run |
| IV. Explicit period | ✅ | Unchanged |
| V. Portability | ✅ | Vendored web component only |
| VI. Secrets | ✅ | None |
| VII. Humans approve money | ✅ | Actions unchanged, still on Oversikt with confirmation |

Post-design re-check: all ✅.

## Project Structure

```text
src/main/java/no/digdir/forsystem/
├── common/HendelsesloggRepository.java   + findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc
└── web/
    ├── FakturakjoringController.java     tabs: /{id}, /{id}/kontroller, /{id}/fakturaer, /{id}/filer,
    │                                     /{id}/historikk; /{id}/avstemming → redirect; list rows
    ├── Fremdrift.java                    NEW progress strip
    ├── Fakturautvalg.java                NEW search/sort/paging
    └── Kjoringsrad.java                  NEW run-list row
src/main/resources/
├── templates/kjoring/
│   ├── fragmenter.html                   NEW: hode(fane), status(s), fremdrift
│   ├── detalj.html                       Oversikt: strip, panel, summary
│   ├── kontroller.html                   NEW (absorbs avstemming.html)
│   ├── fakturaer.html, filer.html, historikk.html   NEW
│   ├── faktura.html, lg04-kontroll.html  breadcrumbs through their tab
│   └── liste.html                        richer rows
└── static/css/forsystem.css              + .faner, .fremdrift, sortable link headers
src/test/java/no/digdir/forsystem/
├── web/{FremdriftTest,FakturautvalgTest}.java          NEW
├── billing/KjoringFanerIT.java                         NEW
└── web/TemplateKonvensjonTest.java       exempt fragmenter.html
```

## Complexity Tracking

None.
