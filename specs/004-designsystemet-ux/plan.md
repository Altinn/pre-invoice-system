# Implementation Plan: Digdir look and feel with Designsystemet

**Branch**: `004-designsystemet-ux` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-designsystemet-ux/spec.md`

## Summary

This change brings the web UI onto the current Digdir Designsystemet:

- **Assets.** Upgrade the vendored CSS to 1.23.0 and swap the deprecated Digdir theme for the
  current one. Self-host Inter 400/500/600. Add the vanilla `designsystemet-web` bundle.
- **Shared layer.** Add one small app stylesheet built on tokens (it replaces about 50 inline
  styles) and one small app script (validation messages, confirmation dialogs).
- **Templates.**
  - A header that marks the current section.
  - A skip link and breadcrumbs.
  - Descriptions on fields, and "Valgfritt" tags on optional ones.
  - Alerts with headings.
  - Confirmation dialogs on Godkjenn, Forkast and Eksporter.
  - Hover tables with tabular numbers.
  - Follows the OS light/dark preference.

Templates and static assets change. The Java side gets no change beyond the new tests. See
[research.md](./research.md) for R1–R7 and D1–D13.

## Technical Context

**Language/Version**: Java 21. Thymeleaf templates, CSS, vanilla JS (ES2020, no build step).

**Primary Dependencies**: Spring Boot 3.5.x, Thymeleaf, htmx 2 (already vendored). New vendored
assets:

- `@digdir/designsystemet-css@1.23.0`
- the Digdir theme v1.23.0
- `@digdir/designsystemet-web@1.23.0` (UMD build)
- Inter 4.1, OFL

All are MIT or OFL, served from `src/main/resources/static/`. There are no npm or Maven additions.

**Storage**: None. No schema change.

**Testing**: JUnit 5. A template-convention unit test. A MockMvc IT over the main pages
(Testcontainers, existing `IntegrationTest` base). A manual browser check in light and dark mode,
following quickstart.md.

**Target Platform**: Evergreen browsers (Edge/Chrome, Firefox, Safari) on Digdir laptops. AKS in
production, `docker compose` locally.

**Project Type**: Server-rendered web application (single Maven module)

**Performance Goals**: Static payload grows by about 425 KB: three fonts of about 113 KB each, plus
the web bundle (84 KB). All of it is cacheable, and htmx boost means it loads once per session.

**Constraints**:
- no third-party network requests (Constitution V)
- htmx-boosted navigation must keep working
- actions must work without JS
- LG04, routes and form field names unchanged

**Scale/Scope**: 21 templates, about 1,300 lines, 6 sections, used by a handful of internal users.

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | No business values touched. The Digdir logo is held back pending a brand answer, not invented |
| II. LG04 contractual | ✅ | Not touched. Golden-file tests run unchanged |
| III. Traceability | ✅ | No change to data or the audit trail. Downloads unchanged |
| IV. Explicit period | ✅ | Not touched |
| V. Portability | ✅ | Every asset is vendored. A test rejects external URLs in templates |
| VI. Secrets | ✅ | None |
| VII. Humans approve money | ✅ | Strengthened: Godkjenn and Eksporter now need an explicit confirmation. Role checks unchanged |

Post-design re-check: all ✅. No complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-designsystemet-ux/
├── plan.md
├── research.md
├── data-model.md        # no data change; records that
├── quickstart.md
├── contracts/ui.md      # layout fragments + markup conventions every page follows
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/resources/
├── static/
│   ├── css/
│   │   ├── designsystemet.css        # replaced: css@1.23.0 dist/src/index.css
│   │   ├── digdir-theme.css          # replaced: internal/digdir/themes/digdir.css @ v1.23.0
│   │   ├── forsystem.css             # NEW: font-face, body font, header, layout, utilities, print
│   │   └── fonts/                    # NEW: Inter-{Regular,Medium,SemiBold}.woff2 + LICENSE.txt
│   └── js/
│       ├── htmx.min.js               # unchanged
│       ├── designsystemet-web.js     # NEW: designsystemet-web@1.23.0 dist/umd/index.js
│       └── forsystem.js              # NEW: validation messages + confirm dialogs (delegated)
└── templates/
    ├── layout.html                   # head(), topbar(seksjon), skiplink, csrf
    └── **/*.html                     # every page migrated to contracts/ui.md

src/test/java/no/digdir/forsystem/web/
├── TemplateKonvensjonTest.java       # NEW unit test: no inline style, no external URL, lang=nb, fragments
└── LayoutIT.java                     # NEW: main pages render header/current section/skip link; assets served
```

**Structure Decision**: Keep the existing single module and the fragment approach in `layout.html`.
App-specific CSS and JS live in one file each, next to the vendored files. README's
vendored-assets line is updated with versions.

## Complexity Tracking

None.
