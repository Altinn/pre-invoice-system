# Implementation Plan: Læringssenter — help and onboarding inside forsystem

**Branch**: `feat/016-laeringssenter` | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: [spec.md](./spec.md), with the groundwork in [forarbeid.md](./forarbeid.md)

## Summary

Help articles are Markdown files under `src/main/resources/hjelp/`, with YAML front matter. `meny.yml`
gives the sections and their order, and `kontrollfunn.yml` gives a "Slik løser du det" text per control
code.

At startup, `web.hjelp.Hjelpebibliotek` reads all of them once, renders them with commonmark-java 0.32.0
(tables, front matter, heading anchors, GFM alerts), checks them, and holds the result in memory. Any
problem stops startup with one message listing everything wrong, so `mvn verify` catches broken content
before merge.

`HjelpController` serves `/hjelp` and `/hjelp/{seksjon}/{side}` through Thymeleaf templates in the
three-column layout. Rendering maps Markdown onto Designsystemet:

- Alerts become `ds-alert`.
- `[Knapp](knapp:)` becomes a button-styled name.
- `{{blokk}}` placeholders become Thymeleaf fragments; the control-code reference is generated from
  `Kontrollkode`.

Relative `.md` links work on GitHub and are rewritten to `/hjelp/…` in the app. `HjelpLenkerIT` checks
every app link against the real GET routes.

A **Hjelp** link joins the top bar. The Kontroller tab links to the reference. `error.html` gets a proper
404. The specs/015 placeholder `/laering` becomes a redirect.

Phase 2 moves `docs/brukerveiledning.md` into articles. Phase 3 adds per-page help links and in-memory
search.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:
- Existing: Spring Boot 3.5.14, Spring MVC, Thymeleaf + htmx, Designsystemet CSS, SnakeYAML 2.4 (through
  Boot).
- New: `org.commonmark:commonmark`, `-ext-gfm-tables`, `-ext-yaml-front-matter`, `-ext-heading-anchor` and
  `-ext-gfm-alerts`, all 0.32.0 (BSD-2, no transitive dependencies).

**Storage**: Classpath files only; no database change and no migration.

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers (existing `IntegrationTest` base). The LG04 golden
file is unchanged.

**Target Platform**: Linux container; local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: Pages are served from memory with no I/O per request. Loading about 45 files at
startup takes well under 1 s.

**Constraints**:
- No assets or links to the internet.
- No raw HTML from content.
- Bokmål.
- Existing UI rules (`TemplateKonvensjonTest`).
- Read-only.

**Scale/Scope**: About 45 articles, 6 sections, about 6 users.

## Constitution Check

*Gate before Phase 0: PASS. Re-checked after design: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS, as a content rule. Articles state only confirmed behaviour. Unconfirmed facts (OQ-6 negative corrections, OQ-23 text limits, OQ-2 kontering) are named as open questions, never guessed. The control-code definitions come from the enum. |
| II. LG04 Output Is Contractual | PASS. Untouched. |
| III. Traceability Is Structural | PASS. Not affected; the Forstå systemet articles explain it. |
| IV. Explicit Billing Period | PASS. No period logic. Articles teach that the period is always chosen explicitly. |
| V. Portability Behind Ports | PASS. One small pure-Java dependency, no external services, nothing loaded from the internet. |
| VI. Secrets From Configuration Only | PASS. Nothing secret. |
| VII. Humans Approve Money | PASS. Read-only feature. |

## Project Structure

```text
specs/016-laeringssenter/ (spec, forarbeid, plan, research, data-model, quickstart, contracts/ui.md, checklists, tasks)

pom.xml                                                   # + commonmark 0.32.0 artifacts
src/main/java/no/digdir/forsystem/web/hjelp/
├── Hjelpebibliotek.java      # NEW: loads, renders, validates, holds articles + menu + kontrollfunn
├── HjelpRenderer.java        # NEW: commonmark setup, ds-* attributes, alerts, knapp:, .md link rewrite, blocks
├── Artikkel.java             # NEW: record (id, tittel, sammendrag, rolle, apneIAppen, relatert, oppdatert, deler, innhold, tekst)
├── Meny.java                 # NEW: sections / groups / sti / jegVil
├── Hjelpesok.java            # NEW (phase 3): in-memory search
└── HjelpController.java      # NEW: GET /hjelp, /hjelp/{seksjon}/{side}, /hjelp/sok, /laering → /hjelp
src/main/java/no/digdir/forsystem/web/HomeController.java # drop the specs/015 /laering placeholder (if merged)
src/main/resources/hjelp/                                 # NEW: meny.yml, kontrollfunn.yml, <seksjon>/*.md
src/main/resources/templates/hjelp/start.html             # NEW
src/main/resources/templates/hjelp/artikkel.html          # NEW
src/main/resources/templates/hjelp/sok.html               # NEW (phase 3)
src/main/resources/templates/hjelp/fragmenter.html        # NEW: menu, aside, stepper, blocks (kontrollkoder, livslop-*)
src/main/resources/templates/layout.html                  # Hjelp link in the top bar; phase 3: hjelp(id) fragment
src/main/resources/templates/kjoring/kontroller.html      # «Les mer i Læringssenteret»
src/main/resources/templates/error.html                   # 404 «Fant ikke siden»
src/main/resources/templates/index.html, laering.html     # specs/015: card → /hjelp, delete placeholder
src/main/resources/static/css/forsystem.css               # .hjelp-oppsett grid, .artikkel, .knapp, .stegviser, .livslop, print
src/test/java/no/digdir/forsystem/web/hjelp/HjelpebibliotekTest.java  # NEW (unit, with fixtures under src/test/resources/hjelp-feil/)
src/test/java/no/digdir/forsystem/web/hjelp/HjelpRendererTest.java    # NEW
src/test/java/no/digdir/forsystem/web/hjelp/HjelpIT.java              # NEW
src/test/java/no/digdir/forsystem/web/hjelp/HjelpLenkerIT.java        # NEW
src/test/java/no/digdir/forsystem/web/LayoutIT.java                   # + /hjelp row
docs/brukerveiledning.md                                  # phase 2: pointer only
docs/funksjonsoversikt.md (F-HJELP-*), docs/09-beslutningslogg.md (B-nn: Markdown in repo, /hjelp)
CLAUDE.md, .specify/memory/constitution.md (1.0.1)        # definition of done names the learning center (FR-018)
```

**Structure Decision**: A new `web.hjelp` package. The feature is pure presentation with no domain logic,
so it sits beside the other web code and depends only on `billing.Kontrollkode`. Content lives under
`src/main/resources/hjelp/` so it ships in the jar and stays readable on GitHub.

## Delivery

| PR | Scope | Content |
|---|---|---|
| **1** | FR-001 – FR-018, FR-023; US1 + US2 | Engine, layout, start page, Hjelp link, 404, Kontroller link, `/laering` redirect, all checks. Articles: Kom i gang (4), Månedsrutinen (6), Når noe går galt (1 overview), Oppslag: Kontrollfunn + Statuser (2). |
| **2** | FR-019, FR-020; US3 | Slik gjør du (about 18), Når noe går galt split per symptom (about 9), Oppslag: Roller, Filer, CSV-format, Ordliste, Forstå systemet (about 6), korrigeringskjøring; brukerveiledning.md becomes a pointer. «Finn og åpne en kunde» waits for the customer-page decision. |
| **3** | FR-021, FR-022; US4 | `layout :: hjelp(id)` on the main pages, search. |

Content for PR 1 is drafted from brukerveiledning §1–3, §8, §10–13 and the runbook's monthly procedure,
then checked against tt02. Økonomi reviews it before release (SC-007).

## Sequencing with specs/015

specs/015 (start-page dashboard, not merged) touches `index.html`, `HomeController`, `forsystem.css` and adds
`laering.html`. If 015 merges first, PR 1 rebases onto it: it deletes the placeholder, points the card to
`/hjelp`, and adds the redirect. If PR 1 merges first, 015 drops its placeholder and links to `/hjelp`.
Either way, the CSS additions go in separate commented blocks to keep conflicts trivial.

## Complexity Tracking

No constitution violations to justify.
