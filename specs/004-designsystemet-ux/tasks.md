---
description: "Task list for adopting Digdir Designsystemet"
---

# Tasks: Digdir look and feel with Designsystemet

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[contracts/ui.md](./contracts/ui.md), [quickstart.md](./quickstart.md)

**Tests**: FR-021 calls for automated tests. Write each one first and watch it fail.

**Paths**:

- `R` = `src/main/resources`
- `T` = `src/test/java/no/digdir/forsystem`
- `S` = the scratchpad copies downloaded during research. They are verified upstream files:
  - css@1.23.0 `dist/src/index.css`
  - theme `internal/digdir/themes/digdir.css` @ v1.23.0
  - web@1.23.0 `dist/umd/index.js`
  - Inter 4.1 Regular/Medium/SemiBold `.woff2` and LICENSE

**Section per template** (the `topbar(...)` argument):

| Section | Templates |
|---|---|
| `''` | `index`, `feil`, `error` |
| `produkter` | `produkt/*` |
| `prisversjoner` | `prisversjon/*` |
| `kunder` | `kunde/*` |
| `bruksdata` | `bruksdata/*` |
| `kjoringer` | `kjoring/*` |
| `hendelseslogg` | `hendelseslogg/*` |

## Phase 1: Setup (vendored assets)

- [x] T001 [P] Replace `R/static/css/designsystemet.css` with css@1.23.0 `index.css`, and `R/static/css/digdir-theme.css` with the v1.23.0 Digdir theme.
- [x] T002 [P] Add `R/static/css/fonts/Inter-Regular.woff2`, `Inter-Medium.woff2`, `Inter-SemiBold.woff2` and `LICENSE.txt` (OFL).
- [x] T003 [P] Add `R/static/js/designsystemet-web.js` (web@1.23.0 UMD).
- [x] T004 [P] In `README.md`, update the vendored-assets paragraph: name the versions and Inter (OFL).

## Phase 2: Foundational (shared head, stylesheet, guard test)

**Blocks every story.**

- [x] T005 Write `T/web/TemplateKonvensjonTest.java` (plain JUnit). It walks `R/templates/**/*.html` and asserts that:
  - no template has an inline `style=` or `th:style=` attribute
  - no `href`/`src` holds an `http(s)://` URL
  - every page except `layout.html` has `lang="nb"`, uses `layout :: head(` and `layout :: topbar(`, and contains `id="hovedinnhold"`

  It must fail now.
- [x] T006 Create `R/static/css/forsystem.css` (unlayered, tokens only, no literal colours) with:
  - `@font-face` for Inter 400/500/600 (`font-display: swap`)
  - `body { font-family: var(--ds-font-family), sans-serif; font-feature-settings: 'cv05' 1; margin: 0 }`
  - `.side` container with vertical rhythm (`> * + *` margin `--ds-size-4`, and `h2` before a section margin `--ds-size-10`)
  - the layout classes from contracts/ui.md: `topprad`, `rad`, `skjema`, `smal`, `bred`, `kort-rad`, `hoyre` (end-aligned, tabular-nums), `avvik`, `tom`, `ikke-utskrift`
- [x] T007 Rewrite `head(tittel)` in `R/templates/layout.html`:
  - add `<meta name="color-scheme" content="light dark">` and `<meta name="text-scale" content="scale">`
  - load the CSS, then the theme, then `forsystem.css`
  - load `htmx.min.js`, `designsystemet-web.js` and `forsystem.js` with `defer`
  - move the inline `<style>` block into `forsystem.css`
- [x] T008 Create `R/static/js/forsystem.js`: an IIFE with `'use strict'`, empty handlers to fill in under US3 and US4, and a comment explaining that it uses document-level delegation so it survives htmx swaps.

**Checkpoint**: The app renders as before, now in Inter on the new theme.

## Phase 3: US1 — the app looks like a Digdir service (P1) 🎯 MVP

**Goal**: Every page uses the current Digdir theme, the Inter font and tokens, with no inline styles.

**Independent test**: quickstart step 1, and `TemplateKonvensjonTest` passes on style, URL and lang.

- [x] T009 [P] [US1] In `R/templates/index.html`:
  - set `lang="nb"` and wrap the content in `main#hovedinnhold`
  - make the cards `a.ds-card` with `data-variant="tinted"`, the link style removed by a class and not inline
  - add the missing cards for Bruksdata and Kjøringer, so all six sections have one
- [x] T010 [P] [US1] In `R/templates/produkt/{liste,rediger,kildenavn}.html`: set `lang="nb"`, add `main#hovedinnhold`, replace inline styles with classes.
- [x] T011 [P] [US1] Do the same in `R/templates/prisversjon/{liste,detalj}.html`.
- [x] T012 [P] [US1] Do the same in `R/templates/kunde/{liste,ny,detalj}.html`.
- [x] T013 [P] [US1] Do the same in `R/templates/bruksdata/{liste,forhandsvis,detalj}.html`.
- [x] T014 [P] [US1] Do the same in `R/templates/kjoring/{liste,detalj,faktura,avstemming,lg04-kontroll}.html`. On avstemming, the `avvik` row highlight uses `th:classappend`.
- [x] T015 [P] [US1] Do the same in `R/templates/hendelseslogg/liste.html`, `R/templates/feil.html` and `R/templates/error.html`.

**Checkpoint**: `TemplateKonvensjonTest` passes, except for the topbar check, which US2 completes.

## Phase 4: US2 — know where I am (P1)

**Goal**: The header marks the current section, every page has a skip link, and detail pages show
breadcrumbs.

**Independent test**: quickstart steps 2, 3 and 9, plus `LayoutIT`.

- [x] T016 [US2] Write `T/web/LayoutIT.java` (extends `IntegrationTest`, MockMvc, `@WithMockUser(roles = "LESER")`). For each of `/`, `/produkter`, `/prisversjoner`, `/kunder`, `/kunder/ny` (FORVALTER), `/bruksdata`, `/kjoringer` and `/hendelseslogg`, assert that the page has:
  - `lang="nb"` and `class="ds-skip-link"`
  - `id="hovedinnhold"`
  - exactly one `aria-current="page"` in the header, on the expected section's link (none on `/`)

  Also assert that `/css/fonts/Inter-Regular.woff2`, `/js/designsystemet-web.js` and `/js/forsystem.js` are served with 200.
- [x] T017 [US2] In `R/templates/layout.html`, replace `topbar` with `topbar(seksjon)`. It is a `th:block` that renders:
  - `a.ds-skip-link` "Hopp til hovedinnhold" pointing to `#hovedinnhold`
  - `header.topbar.ikke-utskrift`, containing the brand (a "forsystem" link plus a small "Digdir" label) and `nav[aria-label=Hovedmeny]` with the six links. The current link gets `th:attr="aria-current=${seksjon == 'x'} ? 'page'"`.

  Add the header styles to `R/static/css/forsystem.css`: token colours, an accent underline on `[aria-current=page]`, a hover state, focus left to the DS defaults, and wrapping on narrow screens.
- [x] T018 [US2] In every page template, change the header include to `<th:block th:replace="~{layout :: topbar('<section>')}"></th:block>`, using the section table above.
- [x] T019 [US2] Replace each "← X" link with `nav.ds-breadcrumbs`, following contracts/ui.md:
  - `produkt/{rediger,kildenavn}`: Forside › Produkter › page
  - `prisversjon/detalj`: Forside › Prisversjoner › version
  - `kunde/{ny,detalj}`: Forside › Kunder › page
  - `bruksdata/{forhandsvis,detalj}`: Forside › Bruksdata › page
  - `kjoring/detalj`: Forside › Kjøringer › Kjøring *periode*
  - `kjoring/{faktura,avstemming,lg04-kontroll}`: Forside › Kjøringer › Kjøring *periode* › page

  The error pages keep only their Tilbake button (US3).

**Checkpoint**: `TemplateKonvensjonTest` and `LayoutIT` pass. Commit
`feat(web): Digdir theme 1.23, Inter, header with current section, skip link and breadcrumbs`.

## Phase 5: US3 — forms explain themselves (P2)

**Goal**: Hints become descriptions, optional fields are tagged, browser validation shows DS
messages, and the error pages look the part.

**Independent test**: quickstart steps 4 and 5.

- [x] T020 [US3] Fill in the validation part of `R/static/js/forsystem.js`:
  - Listen for `invalid` in the capture phase on `document`. For a target inside `ds-field` that has a `[data-field=validation]` paragraph, call `preventDefault()`, set the text to `data-feilmelding` (or `validationMessage` when that is missing) and unhide it. Focus the first invalid field of the form, at most once per 100 ms.
  - On `input`/`change`, once the value is valid, hide and empty the message again.
- [x] T021 [P] [US3] Convert the fields in `R/templates/kunde/{ny,detalj}.html` to `<ds-field>`:
  - Organisasjonsnummer gets the description "9 siffer" and `data-feilmelding`.
  - Fakturamottaker gets a "Valgfritt" tag and the description "Fyll ut bare hvis fakturaen skal til en annen virksomhet".
  - Add a validation paragraph wherever a field has `required` or `pattern`.
- [x] T022 [P] [US3] Do the same in `R/templates/produkt/{liste,rediger,kildenavn}.html`: move bracketed hints to descriptions and tag optional fields.
- [x] T023 [P] [US3] Do the same in `R/templates/prisversjon/{liste,detalj}.html`, keeping the gyldig fra/til help as a description.
- [x] T024 [P] [US3] Do the same in `R/templates/bruksdata/liste.html` (period and file fields) and `R/templates/kjoring/liste.html` (period).
- [x] T025 [P] [US3] Restyle `R/templates/feil.html` and `R/templates/error.html`:
  - `h1`, then `div.ds-alert[data-color=danger][role=alert]` with the message
  - a `button.ds-button[data-variant=secondary]` labelled "Tilbake" that calls `history.back()`, plus a link to the start page
  - error.html keeps the status and timestamp as a `ds-paragraph` sm

**Checkpoint**: Commit `feat(web): field descriptions, optional tags and inline validation messages`.

## Phase 6: US4 — confirm before irreversible actions (P2)

**Goal**: Godkjenn, Forkast and Eksporter open a confirmation dialog, and still submit without JS.

**Independent test**: quickstart step 6.

- [x] T026 [US4] Fill in the confirmation part of `R/static/js/forsystem.js`. A click (delegated) on `button[data-bekreft]` that is not disabled, where the referenced `<dialog>` exists and supports `showModal`, calls `preventDefault()` and then `dialog.showModal()`.
- [x] T027 [US4] In `R/templates/kjoring/detalj.html`, give each of the four action forms (Godkjenn, Forkast ×2, Eksporter) a trigger with `data-bekreft` and a `dialog.ds-dialog[closedby=any]`, following contracts/ui.md. Each dialog has:
  - a heading naming the action and `${kjoring.periode()}`
  - one sentence on the consequence:
    - Godkjenn: "Kjøringen låses og kan eksporteres."
    - Forkast: "Kjøringen markeres som forkastet og kan ikke godkjennes."
    - Eksporter: "LG04 og vedlegg genereres og arkiveres."
  - a confirm button ("Ja, …") and an Avbryt button using `command=close`
- [x] T028 [US4] Add a test in `LayoutIT` (or the existing kjøring IT) asserting that the generated-run page renders `data-bekreft` on Godkjenn/Forkast and that each id matches a `<dialog`. Posting to `/godkjenn` directly still works (no server change).

**Checkpoint**: Commit `feat(web): confirm approve, discard and export in a dialog`.

## Phase 7: US5 — tables, statuses and messages are easy to scan (P2)

**Goal**: Hover tables with aligned numbers, findings shown as alerts with headings, plain empty
states, and collapsible long help.

**Independent test**: quickstart steps 7 and 8.

- [x] T029 [P] [US5] Add `data-hover` to every data `table.ds-table`, but not to key/value summary tables. Make sure every numeric `th`/`td` carries `hoyre`. Give each table that has no heading directly above it a `<caption>`. Applies to all templates.
- [x] T030 [P] [US5] In `R/templates/kjoring/detalj.html`:
  - turn the findings into `div.ds-alert` with an `h3.ds-heading[data-size=2xs]` holding the code, then a `p` with the message
  - turn the other `p.ds-alert` into `div.ds-alert > p`
- [x] T031 [P] [US5] Turn `p.ds-alert` into `div.ds-alert`, with headings where a message has more than one part, in `R/templates/bruksdata/{liste,forhandsvis,detalj}.html`, `R/templates/kjoring/{avstemming,lg04-kontroll,faktura,liste}.html`, `R/templates/prisversjon/*` and `R/templates/kunde/*`.
- [x] T032 [P] [US5] Make every empty-list row use `td.tom` and a sentence that, where useful, names the next step (for example "Ingen kjøringer ennå. Velg en periode over og trykk Generer."). Applies to all list templates.
- [x] T033 [P] [US5] In `R/templates/bruksdata/liste.html`, move the CSV-format and datavarehus-export explanations into `details.ds-details` ("Om CSV-formatet", "Om eksportfilen"). Do the same for any other help text longer than about three lines.

**Checkpoint**: Commit `feat(web): scannable tables, findings as alerts, empty states`.

## Phase 8: US6 — follow light or dark mode (P3)

**Goal**: Pages follow the OS light/dark preference.

**Independent test**: quickstart steps 1–7 in OS dark mode.

- [x] T034 [US6] Set `data-color-scheme="auto"` on `<body>` in every template. `TemplateKonvensjonTest` asserts that no template uses `data-color-scheme="light"`.
- [x] T035 [US6] Audit `R/static/css/forsystem.css` for literal colours (there should be none) and check every page in dark mode in the browser.

**Checkpoint**: Commit `feat(web): follow the OS light/dark preference`.

## Phase 9: Polish

- [x] T036 Add `@media print` to `R/static/css/forsystem.css`: hide `.ikke-utskrift`, `.ds-skip-link`, `.ds-breadcrumbs`, `dialog` and the action `.rad` forms. Give the action rows on `kjoring/detalj` the `ikke-utskrift` class.
- [x] T037 Run quickstart.md end to end in the browser, in light and dark mode, at desktop and 375 px. Fix whatever it finds.
- [x] T038 Update docs:
  - add a line to `docs/02-architecture.md`, UI section, on vendored Designsystemet 1.23, the theme source and `designsystemet-web`
  - set the spec status to Implemented
- [x] T039 Run `./mvnw verify` green, including the LG04 golden files, then make the final commit.

## Implementation notes (2026-10-22)

- **One commit.** The stories touch the same templates, so the work landed as a single commit
  instead of one per checkpoint. The PR is squash-merged anyway.
- **T028.** The test lives in `T/billing/KjoringBekreftelseIT.java`, because the run fixture is
  package-private to `billing`.
- **More dialogs.** The price-version actions Aktiver and Arkiver got the same confirmation dialog.
  Cancel has the initial focus in every dialog. spec FR-012 is updated to match.
- **Delayed open.** `forsystem.js` opens the dialog one tick after the click. Otherwise the
  `closedby="any"` light dismiss sees the same click as a click outside the dialog and closes it
  again.
- **Alert margins.** Alert children have no default margins, so the alert icon lines up with the
  first line of text.
- **T037.** Checked in the browser:
  - light and dark mode, and 375 px width
  - validation, including `aria-invalid`/`aria-describedby` set by `<ds-field>`
  - the Forkast, Godkjenn and Arkiver dialogs, both cancel and confirm
  - the error page

  Print preview was checked from the CSS only, not visually.

## Dependencies

- **Setup → Foundational → US1 → US2.**
  - US1 and US2 touch every template, so they run in order and share one commit.
  - Within a phase, tasks marked [P] touch different files.
- **US3, US4, US5 and US6** each depend only on Foundational and US2. They touch overlapping
  templates, so run them one after another in the order given. Inside each, the [P] tasks touch
  different files.
- **Polish** comes last.

## Parallel examples

- **Setup**: T001–T004 all run in parallel.
- **US1**: T009–T015 cover one template group each.
- **US3**: T021–T025 cover separate template groups, after T020.

## Implementation strategy

The MVP is Setup, Foundational and US1: the visual identity, which fixes the serif fallback on every
page. US2 completes the navigation in the same commit. US3–US5 are independent increments, each
with its own commit. US6 is cheap because everything is built on tokens.
