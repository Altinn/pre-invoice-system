# UI contract: page skeleton and markup conventions

Every page template follows this contract. `TemplateKonvensjonTest` and `LayoutIT` check the
parts marked ✓.

## Page skeleton

```html
<!DOCTYPE html>
<html lang="nb" xmlns:th="http://www.thymeleaf.org">                        <!-- ✓ lang="nb" -->
<head th:replace="~{layout :: head('Kunder')}"></head>                      <!-- ✓ shared head -->
<body data-color-scheme="auto" data-size="md" data-color="neutral" hx-boost="true">
<th:block th:replace="~{layout :: topbar('kunder')}"></th:block>            <!-- ✓ skip link + header -->
<main id="hovedinnhold" class="side" tabindex="-1">
  <nav class="ds-breadcrumbs" aria-label="Du er her:">…</nav>               <!-- detail pages only -->
  <h1 class="ds-heading" data-size="lg">…</h1>
  …
</main>
</body>
</html>
```

**Header** (`layout :: topbar(seksjon)`).

- `seksjon` is one of `produkter|prisversjoner|kunder|bruksdata|kjoringer|hendelseslogg`, or `''`
  when no section applies.
- The fragment renders:
  - an `a.ds-skip-link` to `#hovedinnhold`
  - `header.topbar` containing the product name (a link to `/`)
  - `nav[aria-label="Hovedmeny"]` with the six links. The link for `seksjon` gets
    `aria-current="page"` (✓).

**Head** (`layout :: head(tittel)`) loads only local assets, in this order:

1. `designsystemet.css`
2. `digdir-theme.css`
3. `forsystem.css`
4. `htmx.min.js` (defer)
5. `designsystemet-web.js` (defer)
6. `forsystem.js` (defer)

No `http(s)://` URL appears in any template (✓).

**No inline `style` attributes** (✓). Use the classes in `forsystem.css`:

| Class | Use |
|---|---|
| `side` | Page container (max width, gutters, vertical rhythm) |
| `topprad` | Heading row with an action on the right (title + "Ny kunde") |
| `rad` | Horizontal group of buttons, or inline form controls aligned at the bottom |
| `skjema` | Vertical form layout, max 32rem |
| `smal` | Max 32rem block (summary tables) |
| `bred` | Max 40rem block (prose, alerts) |
| `kort-rad` | Responsive card grid |
| `hoyre` | Numeric/action cell: right-aligned, tabular numbers |
| `avvik` | Table row that needs attention (warning surface) |
| `ikke-utskrift` | Hidden in print |

## Breadcrumbs (detail pages)

```html
<nav class="ds-breadcrumbs" aria-label="Du er her:">
  <a class="ds-link" th:href="@{/kjoringer}" aria-label="Tilbake til Kjøringer">Kjøringer</a>
  <ol>
    <li><a class="ds-link" th:href="@{/}">Forside</a></li>
    <li><a class="ds-link" th:href="@{/kjoringer}">Kjøringer</a></li>
    <li><a class="ds-link" aria-current="page" th:text="|Kjøring ${…}|">Kjøring 2027-01</a></li>
  </ol>
</nav>
```

Wide screens show the trail. Below 650 px only the single back link shows.

## Fields

```html
<ds-field class="ds-field">
  <label class="ds-label">Organisasjonsnummer</label>
  <p data-field="description">9 siffer</p>
  <input class="ds-input" name="organisasjonsnummer" pattern="[0-9]{9}" required
         data-feilmelding="Organisasjonsnummer må være 9 siffer.">
  <p class="ds-validation-message" data-field="validation" hidden></p>
</ds-field>
```

- `<ds-field>` (from designsystemet-web) wires up the label `for`, `aria-describedby` and
  `aria-invalid`. No ids are needed, but existing ids stay so other code can still find the inputs.
- Hints go in `data-field="description"`, never in brackets in the label.
- An optional field in a mixed form gets the tag in its label:
  `<span class="ds-tag" data-color="info" data-size="sm">Valgfritt</span>`.
  When every field in a form is required, a single sentence above the form says so instead
  ("Alle felt må fylles ut."). Asterisks are never used.
- Inputs with a format rule carry `data-feilmelding`. `forsystem.js` shows that message in the
  validation paragraph on `invalid` and hides it again once the value is valid.

## Alerts

- **One-liner**: `<div class="ds-alert" data-color="info" role="status"><p>…</p></div>`
- **Finding**: `<div class="ds-alert" data-color="danger"><h3 class="ds-heading" data-size="2xs">KODE</h3><p>…</p></div>`
- Use `role="alert"` only for messages that appear after the user acts (for example an error page),
  and `role="status"` for informational messages.

## Confirmation dialog (Godkjenn, Forkast, Eksporter)

```html
<form th:action="@{/kjoringer/{id}/forkast(id=…)}" method="post">
  <th:block th:replace="~{layout :: csrf}"></th:block>
  <button class="ds-button" data-variant="secondary" data-color="danger" type="submit"
          data-bekreft="bekreft-forkast">Forkast</button>
  <dialog class="ds-dialog" id="bekreft-forkast" closedby="any" aria-labelledby="bekreft-forkast-tittel">
    <h2 class="ds-heading" data-size="xs" id="bekreft-forkast-tittel">Forkaste kjøringen for 2027-01?</h2>
    <p class="ds-paragraph">Kjøringen markeres som forkastet. …</p>
    <div class="rad">
      <button class="ds-button" data-color="danger" type="submit">Ja, forkast</button>
      <button class="ds-button" data-variant="secondary" type="button"
              command="close" commandfor="bekreft-forkast">Avbryt</button>
    </div>
  </dialog>
</form>
```

- **With JS**: clicking a button that has `data-bekreft` opens the dialog instead of submitting.
  The submit button inside the dialog submits the form, so htmx boost and CSRF work as before.
- **Without JS**: the first button submits directly.
- A disabled trigger (Godkjenn while findings are blocking) can't be clicked, so its dialog never
  opens.

## Tables

- `<table class="ds-table" data-size="sm" data-hover>`. When there is no visible heading right
  above the table, add a `<caption>`.
- Numeric cells and their headers use `class="hoyre"`.
- Empty lists render one row, `<td colspan="…" class="tom">Ingen … ennå.</td>`, and the text says
  what to do next where that helps.
