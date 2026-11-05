# Implementation Plan: Clearer texts, labels and number format

**Branch**: `feat/010-tekst-og-tallformat` (stacked on `feat/009-kjoring-fakturatekster`) | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-tekst-og-tallformat/spec.md`

## Summary

This feature is a UI-only sweep, from Økonomi's November review. It has five parts:

- One Thymeleaf helper bean, `@tall`, formats every amount, count, quantity and unit price. It
  replaces the scattered `#numbers.formatDecimal(...)` calls and the raw `${x}` output on the pages
  listed below.
- `Kontrollkode` gains a severity and a Norwegian definition per code. The Kontroller tab renders
  both from the enum.
- The production OIDC provider records `preferred_username` instead of `sub`.
- Everything else is template text: labels, warnings, next-step pointers and column names.
- The invoice detail page gets the customer name, a sum row and one reference line.

There is no schema change, and calculations, controls and LG04 are untouched.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Thymeleaf + htmx, Digdir Designsystemet CSS, Spring
Security OAuth2 client (Entra ID in prod)

**Storage**: PostgreSQL 16 / Flyway. No migration in this feature.

**Testing**: JUnit 5 + AssertJ (unit), Spring Boot + Testcontainers ITs with MockMvc for pages
(existing `Web*IT` pattern)

**Target Platform**: Linux container (Azure Container Apps in prod), local `docker compose up`

**Project Type**: Server-rendered web application

**Performance Goals**: n/a (formatting only)

**Constraints**: LG04 golden file byte-identical (constitution II). No stored value changes.

**Scale/Scope**: About 12 templates, 1 enum, 1 new bean, 1 controller method, 1 config line

## Constitution Check

| Principle | Check |
|---|---|
| I. Never invent business values | ✅ The dimension names and all wording come from Økonomi (Confluence kickoff). The control-code definitions describe forsystem's own behaviour and are not business values. |
| II. LG04 contractual | ✅ The writer is untouched, and the golden test stays in the suite. |
| III. Traceability | ✅ Display only. Bruksdata-id and pris-id stay on invoice lines. |
| IV. Explicit billing period | ✅ Not touched. |
| V. Portability | ✅ The username claim is provider config (`user-name-attribute`), with no Azure SDK involved. The local stub user is unchanged. |
| VI. Secrets from config | ✅ No secrets involved. |
| VII. Humans approve money | ✅ Approval rules are unchanged. The import warning strengthens informed consent. |

Gate: **PASS** (re-checked after design: PASS).

## Project Structure

### Documentation (this feature)

```text
specs/010-tekst-og-tallformat/
├── spec.md
├── plan.md
├── research.md          # R1–R5
├── data-model.md        # Kontrollkode severity + definition
├── contracts/ui-tekster.md   # exact user-facing wording
├── quickstart.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/no/digdir/forsystem/
├── web/Tallformat.java                 # NEW @Component("tall"): belop, antall, mengde, pris
├── web/Dimensjonsnavn.java             # defaults Dim1 Koststed / Dim2 Prosjekt/formål / Dim4 Kappost
├── web/BruksdataController.java        # product name map for the preview
├── web/FakturakjoringController.java   # Kontrollkode list for the explanation; invoice references
├── billing/Kontrollkode.java           # + alvorlighet, + definisjon
└── billing/FakturakjoringService.java  # uses Kontrollkode.alvorlighet() instead of its own split, if any
src/main/resources/
├── application-prod.yaml               # provider.entra.user-name-attribute: preferred_username
└── templates/
    ├── kjoring/{liste,detalj,fakturaer,faktura,kontroller,lg04-kontroll,fragmenter}.html
    ├── bruksdata/{liste,forhandsvis,detalj,sammenligning}.html
    ├── prisversjon/detalj.html
    └── produkt/liste.html
src/test/java/no/digdir/forsystem/
├── web/TallformatTest.java             # NEW
├── billing/KontrollkodeTest.java       # NEW: every code has severity + definition
├── web/OidcBrukernavnTest.java         # NEW: prod config names the username claim
└── existing Web*IT / page ITs          # updated wording and number-format assertions
```

**Structure Decision**: This is the existing single Spring Boot module, so no new packages are
needed. `Tallformat` lives in `web` beside `Dimensjonsnavn`, the other view helper.

## Complexity Tracking

No violations.
