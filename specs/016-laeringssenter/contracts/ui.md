# UI contract: specs/016 Læringssenter

## Endpoints

All of these are GET, readable by any signed-in user (LESER and up), and change nothing.

| Path | Phase | Result |
|---|---|---|
| `/hjelp` | 1 | Læringssenter start page |
| `/hjelp/{seksjon}/{side}` | 1 | Article; unknown → 404 |
| `/laering` | 1* | `302 → /hjelp` (*once specs/015's placeholder is merged; research R8) |
| `/hjelp/sok?q=` | 3 | Search results |

## Top bar (every page)

- A **Hjelp** link at the right end of `.topbar-inner`, outside `nav.hovedmeny`, so `LayoutIT`'s
  one-current-section rule for the main menu still holds.
- `aria-current="page"` on Hjelp when `seksjon == 'hjelp'`.
- On narrow screens the link wraps with the menu and keeps its place at the end.

## Start page `/hjelp`

- Breadcrumbs: Forside › Læringssenter. h1 **Læringssenter**. The intro reads «Lær forsystem: hva det gjør,
  hvordan en måned kjøres, og hva du gjør når noe stopper.»
- Four `ds-card` links:

  | Card | Text | Goes to |
  |---|---|---|
  | **Ny her? Start her** | «Hva forsystem er, rollene, og en rundtur i skjermbildene.» | first Kom i gang article |
  | **Månedsrutinen steg for steg** | «Fra bruksdata til LG04-fil i seks steg.» | Steg 1 |
  | **Noe gikk galt** | «Finn det du ser, og hva du gjør med det.» | Når noe går galt |
  | **Oppslag** | «Kontrollfunn, statuser, filer og ordliste.» | Kontrollfunn |

- h2 **Jeg vil …**: a list of links from `meny.yml`.
- The section menu is shown on the left here too, with nothing marked current.

## Article page `/hjelp/{seksjon}/{side}`

Three columns (`.hjelp-oppsett`, CSS grid): menu about 16rem, article 1fr up to about 44rem, aside about
14rem. Under 64rem the aside moves below the article. Under 48rem the menu becomes
`<details class="ds-details hjelp-meny-liten"><summary>Innhold</summary>…`.

**Left, `nav aria-label="Læringssenter"`**

- One `ds-details` per section, open when it holds the current article.
- Group subheadings where `grupper` is used.
- The current article has `aria-current="page"`.
- In a `sti` section, items are numbered.

**Middle, `article.artikkel`**

- Breadcrumbs: Forside › Læringssenter › {seksjon} › {tittel}.
- h1 {tittel}, then the summary as `p.ingress`.
- A tag «Krever rollen {ROLLE}» when `rolle` is set.
- In a `sti` section: «Steg n av N» and a stepper (`ol.stegviser`, steps done / current / next, with
  `aria-current="step"`).
- The body. Rendered elements get: `ds-heading` with data-size (h2 = sm, h3 = xs), `ds-table` with
  data-size sm, `ds-link` on links, `ds-alert` callouts, `span.knapp` for button names, `code` unchanged.
- At the bottom, in a `sti` section: «‹ Forrige: {tittel}» and «Neste: {tittel} ›» (`ds-link`; no arrow
  character, per `TemplateKonvensjonTest`'s rule against "←" links).
- Footer: «Sist oppdatert {d. MMMM yyyy}».

**Right, `aside aria-label="Om denne siden"`, class `ikke-utskrift`**

- **På denne siden**: links to the h2 anchors; hidden if there are fewer than two.
- **Gå til i appen**: a `ds-button` (secondary) link to `apne-i-appen`; hidden if not set.
- **Relatert**: links to the related articles; hidden if there are none.

## Blocks

- `kontrollkoder`: a `dl` (or a table on wide screens) per code: `<code id="{KODE}">`, a severity tag
  (danger/warning, as on the Kontroller tab), the definition, and **Slik løser du det**: {løsning}.
- `livslop-kjoring` and `livslop-prisversjon`: `ol.livslop` state rows (status tags with arrows drawn in
  CSS, the transition name under each arrow), with a text equivalent for screen readers.

## Changes outside `/hjelp`

- `kjoring/kontroller.html`: under the "Hva betyr kodene?" and "Hva betyr alvorlighet?" explainers, a link
  «Les mer i Læringssenteret» to `/hjelp/oppslag/kontrollfunn`. Phase 3 adds the anchor `#{KODE}` per row.
- `error.html`: on 404, h1 «Fant ikke siden» with links to Forside and Læringssenter.
- Phase 3: each main page gets `layout :: hjelp('{artikkel-id}')`, a small «Hjelp til denne siden» link
  right after the h1.

## Search `/hjelp/sok?q=` (phase 3)

- A search field at the top of the left menu (GET form, `role="search"`).
- Result page: «{n} treff for «{q}»», a list of title, section and summary.
- No hits: «Ingen treff for «{q}».» with links to the start page and *Når noe går galt*.
- Empty or missing `q`: the start page.

## Print

The top bar, menu, aside, stepper and Forrige/Neste are hidden; the article prints at full width.
