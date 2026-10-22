# Research: Digdir look and feel with Designsystemet

Sources checked 2026-10-22: the npm registry, the jsDelivr listings, github.com/digdir/designsystemet
(tag v1.23.0), designsystemet.no (setup, colours, sizes, the patterns "required-and-optional-fields"
and "errors"), and profilveileder.digdir.no.

## Findings

- **R1 (what we ship)**: Both vendored files match an upstream release byte for byte.
  - `static/css/designsystemet.css` is `@digdir/designsystemet-css@1.18.0` `dist/src/index.css`.
  - `static/css/digdir-theme.css` is `@digdir/designsystemet-theme@1.11.0` `brand/digdir.css`.
  - The theme package is deprecated and frozen at tokens v1.8.0.
- **R2 (current upstream)**:
  - `@digdir/designsystemet-css` is at **1.23.0** (2026-09-25).
  - The current Digdir theme is no longer published to npm. It lives in the repo at
    `internal/digdir/themes/digdir.css` (build v1.23.0), generated from
    `internal/digdir/digdir.config.json`. That config sets accent `#0062BA`, neutral `#1E2B3C`,
    brand1–3, border-radius 4 and font Inter.
  - Every `--ds-*` token name in our old theme still exists in the new one.
  - Since 1.18, two classes were added (`ds-file-upload`, `ds-list--unstyled`) and nothing was
    renamed or removed, so the upgrade needs no markup changes.
  - Visible changes:
    - Alert, ErrorSummary and Table use radius `lg` (1.21.1).
    - Labels are semibold by default (1.23).
    - Details has more padding (1.23).
    - A forced-colors mapping was added (1.23).
- **R3 (font)**: The docs say to load Inter at 400/500/600 and set
  `body { font-family: var(--ds-font-family), sans-serif; font-feature-settings: 'cv05' 1; }`
  yourself, because the CSS never sets a font family. Their snippet loads
  `altinncdn.no/fonts/inter/v4.1/inter.css`. Today we set no font at all, so pages fall back to
  Times.
- **R4 (`@digdir/designsystemet-web@1.23.0`)**: This is the vanilla companion package. Its
  `dist/umd/index.js` (84 KB, self-contained, global `Designsystemet`) does three things:
  - It defines the custom elements `ds-field`, `ds-error-summary`, `ds-breadcrumbs`,
    `ds-pagination`, `ds-tabs`, `ds-suggestion` and `ds-tooltip`.
  - It polyfills the invoker commands (`command`/`commandfor`) and `dialog[closedby]`.
  - `<ds-field class="ds-field">` sets the label `for`. It points the input's `aria-describedby` at
    every `[data-field=description|validation]` child. It sets `aria-invalid="true"` while a visible
    danger `[data-field=validation]` is present. A MutationObserver keeps all three up to date.
  - Custom elements upgrade themselves whenever htmx swaps content in, so they need no
    re-initialisation.
- **R5 (missing pieces in our templates)**:
  - The header has no current-page marking.
  - Twelve "← X" back links stand in for breadcrumbs.
  - Hints sit in brackets inside labels, for example "Organisasjonsnummer (9 siffer)".
  - Alerts are `<p>`, so a finding cannot have a heading.
  - Godkjenn, Forkast and Eksporter submit on one click.
  - There are 50 inline `style` attributes, 31 of them `margin-top` on section headings.
  - The language is `lang="no"`. Designsystemet's built-in texts, and screen readers, want `nb`.
- **R6 (security)**: `SecurityConfig` leaves `/css/**` and `/js/**` open. If fonts live under
  `/css/fonts/`, no security change is needed.
- **R7 (layout)**: Designsystemet has no header, page or grid component. Its docs leave app chrome
  to the consumer, built from tokens. The Digdir logo guide doesn't say whether internal tools may
  use the logo.

## Decisions

- **D1 (stylesheets)**: Replace both vendored files.
  - Use css@1.23.0 `index.css`.
  - Use the repo theme `internal/digdir/themes/digdir.css` at tag `v1.23.0`, kept under the same
    file name, `digdir-theme.css`.
  - Load the CSS first, then the theme (the order the setup docs give), then our own
    `forsystem.css`. Everything upstream sits in
    `@layer ds.*`, so our unlayered rules win without `!important`.
  - Rejected:
    - Generating our own theme with the CLI: same result, more moving parts.
    - Keeping the deprecated package: it is frozen at tokens v1.8.
- **D2 (font)**: Self-host Inter 4.1 static Regular, Medium and SemiBold (`.woff2`, OFL) under
  `static/css/fonts/`, with a local `@font-face` in `forsystem.css`.
  - Static weights match the docs (400/500/600) and keep `cv05` predictable.
  - Rejected:
    - The altinncdn stylesheet: it breaks offline/local runs (Constitution V, FR-002).
    - The variable font: one bigger file, and we only use three weights.
- **D3 (web package)**: Vendor `designsystemet-web@1.23.0` `dist/umd/index.js` as
  `static/js/designsystemet-web.js`, loaded with `defer`. We use it for:
  - `<ds-field>` aria wiring (FR-010)
  - invoker and `closedby` polyfills for the confirmation dialog (FR-012)
  - `<ds-error-summary>` focus
  - Rejected: hand-writing the aria wiring, which is more code and drifts from upstream.
- **D4 (app script)**: Add a small `static/js/forsystem.js`. It uses document-level event delegation,
  so it survives htmx swaps. It has two jobs:
  - **Validation (FR-010).**
    - On `invalid` (capture phase): suppress the native bubble and copy the message into the field's
      `[data-field=validation]`. The message is the input's `data-feilmelding`, or
      `validationMessage` if that is missing. Then unhide it. Focus the first invalid field once per
      submit.
    - On `input`/`change`: once the value is valid, hide the message again. `<ds-field>` then clears
      `aria-invalid`.
  - **Confirmation (FR-012/013).**
    - A submit button with `data-bekreft="<dialog-id>"` normally submits its form.
    - Our click handler stops that submit and calls `showModal()` on the named `<dialog>`, which sits
      inside the same form.
    - The dialog's confirm button is a plain submit button of that form, so htmx boost and CSRF behave
      exactly as today.
    - The cancel button uses `command="close" commandfor`.
    - Without scripts, the first button just submits (FR-013).
  - Rejected: putting `command="show-modal"` on the trigger. Invoker commands are ignored on a form's
    submit button, and without scripts the action would become unreachable.
- **D5 (current section)**: The header fragment takes a parameter, `layout :: topbar('kunder')`.
  Each page names its section, and the fragment sets `aria-current="page"` on that link. No Java
  change.
  - Rejected: a `@ControllerAdvice` that derives the section from the request URI. That adds hidden
    coupling for six constant strings.
- **D6 (breadcrumbs)**: Use `nav.ds-breadcrumbs` markup with `aria-label="Du er her:"`.
  - It contains an `ol` of links, and the last item has `aria-current="page"`.
  - It also contains the single "back" `<a>` that the CSS shows instead of the `ol` on narrow
    screens.
  - It is written inline per page, because the trails differ: two or three levels with dynamic
    titles.
- **D7 (spacing)**: Add a vertical rhythm rule, `main > * + * { margin-block-start: var(--ds-size-4) }`,
  plus a larger gap before section headings (`main h2 { margin-block-start: var(--ds-size-10) }`).
  Add a few named layout classes: `rad`, `topprad`, `skjema`, `smal`, `kort-rad`. Together these
  replace all 50 inline styles.
  - The warning-row highlight on avstemming becomes a class, `avvik`, set by `th:classappend`.
- **D8 (tables)**: Every data table gets `data-hover`. Numeric cells use `.hoyre`, which is
  `text-align: end` plus `font-variant-numeric: tabular-nums`. Long text wraps (`overflow-wrap:
  anywhere` on `td`). Zebra stripes are left off: the theme's hover plus borders read cleaner on
  short tables.
- **D9 (colour scheme)**: Use `data-color-scheme="auto"` on `<body>` and
  `<meta name="color-scheme" content="light dark">`. Custom CSS uses only `--ds-color-*` tokens,
  never literal colours.
- **D10 (language)**: Use `<html lang="nb">`.
- **D11 (print)**: Add `@media print`. It hides the header, skip link, breadcrumbs, dialogs and
  action buttons (`.ikke-utskrift`).
- **D12 (logo)**: Show "forsystem" as text, with a small "Digdir" label set in the theme's own text
  colours. The logo waits for Digdir's communications team. This is recorded in the spec's
  Assumptions, not in docs/07, because it is not a business value.
- **D13 (tests)**: Two new tests guard the UI.
  - A unit test (`TemplateKonvensjonTest`) scans `templates/**/*.html`. It fails if a template has:
    - an inline `style` attribute
    - an `http(s)://` URL in an `href` or `src`
    - an old `lang="no"`
    - a page that doesn't use the shared `head`/`topbar` fragments
  - An integration test (`LayoutIT`) GETs every main page. It asserts `lang="nb"`, the skip link,
    `aria-current="page"` on the correct section, and that the vendored assets are served.
