# UI contract: run tabs

Builds on the UI contracts of specs 004 and 005.

## Routes (all GET, readable by LESER)

| Route | Template | Tab |
|---|---|---|
| `/kjoringer/{id}` | `kjoring/detalj` | Oversikt |
| `/kjoringer/{id}/kontroller` | `kjoring/kontroller` | Kontroller |
| `/kjoringer/{id}/fakturaer?q=&sorter=ordre\|kunde\|belop&side=` | `kjoring/fakturaer` | Fakturaer |
| `/kjoringer/{id}/filer` | `kjoring/filer` | Filer |
| `/kjoringer/{id}/historikk` | `kjoring/historikk` | Historikk |
| `/kjoringer/{id}/avstemming` | redirect → `/kjoringer/{id}/kontroller#bruksdata` | — |
| `/kjoringer/{id}/faktura/{fid}` | `kjoring/faktura` (breadcrumbs › Fakturaer) | Fakturaer |
| `/kjoringer/{id}/lg04-kontroll` | `kjoring/lg04-kontroll` (breadcrumbs › Kontroller) | Kontroller |

## Shared header (`kjoring/fragmenter :: hode(fane)`)

```html
<nav class="ds-breadcrumbs">… Kjøringer › Kjøring januar 2027</nav>
<div class="topprad"><h1>Kjøring januar 2027</h1> <span class="ds-tag" data-color=…>Venter på godkjenning</span></div>
<nav class="faner ikke-utskrift" aria-label="Kjøring">
  <ul>
    <li><a href="/kjoringer/1" aria-current="page">Oversikt</a></li>
    <li><a href="/kjoringer/1/kontroller">Kontroller <span class="ds-badge" data-color="danger" data-count="2"></span></a></li>
    <li><a href="/kjoringer/1/fakturaer">Fakturaer <span class="fane-antall">312</span></a></li>
    <li><a href="/kjoringer/1/filer">Filer</a></li>
    <li><a href="/kjoringer/1/historikk">Historikk</a></li>
  </ul>
</nav>
```

A badge's number is also given as hidden text, for example "(2 blokkerende funn)", because the
`::before` content is not reliably read aloud.

## Status labels (`fragmenter :: status(s)`)

| Status | Text | Colour |
|---|---|---|
| GENERERT | Venter på godkjenning | warning |
| GODKJENT | Klar til eksport | info |
| EKSPORTERT | Eksportert | success |
| FORKASTET | Forkastet | neutral |

## Progress strip (`fragmenter :: fremdrift(f)`)

```html
<ol class="fremdrift" aria-label="Fremdrift">
  <li class="ferdig"><span class="ds-sr-only">Ferdig: </span><strong>Generert</strong> <span class="liten">dev, 22.10.2026 16:05</span></li>
  <li class="naa" aria-current="step"><strong>Kontrollert og godkjent</strong></li>
  <li class="senere"><strong>Eksportert</strong></li>
  <li class="senere"><strong>Sendt til regnskap</strong></li>
</ol>
```

A FORKASTET run shows an extra `li.forkastet` with "Forkastet" and who/when after the last done
step. The steps after it are left out.

## Invoice list

The page has:
- a search form (`id="fakturasok"`): `input[type=search][name=q]` and a Søk button
- above the table, the result line: "N fakturaer · sum X kr", or "N av M fakturaer" when searching
- `th[aria-sort] > a` headers for Ordre, Kunde and Sum
- `<ds-pagination class="ds-pagination" data-current data-total data-href>`
