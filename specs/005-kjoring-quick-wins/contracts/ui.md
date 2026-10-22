# UI contract: run page

Builds on [specs/004 contracts/ui.md](../../004-designsystemet-ux/contracts/ui.md).

## Order on `kjoring/detalj`

1. Breadcrumbs (Forside › Kjøringer › Kjøring *januar 2027*), with the title and status tag beside it
2. Stopped-export alert (only when present)
3. **Neste steg** panel (`section.neste-steg`, `aria-labelledby` pointing at its heading)
4. Sammendrag (key/value table)
5. **Kontrollfunn** (`id="kontrollfunn"`): the grouped table, or "Ingen kontrollfunn."
6. **Filer** (`id="filer"`)
7. Fakturaer
8. LG04-avstemming details (only after export)

## Neste steg per status

| Status | Heading | Checklist (`ul.sjekkliste > li.{ok,feil,advarsel,ukjent}`) | Actions |
|---|---|---|---|
| GENERERT | Kontroller og godkjenn | Bruksdata (link to the avstemming page) · Blokkerende funn (link `#kontrollfunn`) · Advarsler (link `#kontrollfunn`) | Godkjenn (`data-bekreft`, disabled while blocking findings exist, with the reason) · Forkast (`data-bekreft`) |
| GODKJENT | Eksporter til regnskap | Godkjent av *X* *dato* (ok) | Eksporter (`data-bekreft`) · Forkast (`data-bekreft`) |
| EKSPORTERT | Send tallene til regnskap | LG04 stemmer / stemmer IKKE (ok/feil) · Eksportert av *X* *dato* when known | "Til e-post til regnskap" figures + Kopier |
| FORKASTET | Kjøringen er forkastet | — | Generer januar 2027 på nytt (`POST /kjoringer`, `periode=ÅÅÅÅ-MM`) |

## Findings table

```html
<table class="ds-table" data-size="sm">
  <thead><tr><th>Alvorlighet</th><th>Kode</th><th class="hoyre">Antall</th><th>Eksempel</th></tr></thead>
  <tbody th:each="g : ${funngrupper}">
    <tr>
      <td><span class="ds-tag" data-color="danger|warning">Blokkerende|Advarsel</span></td>
      <td><code>MANGLER_KUNDE</code></td><td class="hoyre">2</td><td>first message</td>
    </tr>
    <tr th:if="${g.antall() > 1}"><td colspan="4">
      <details class="ds-details"><summary>Vis alle 2</summary><ul class="ds-list">…</ul></details>
    </td></tr>
  </tbody>
</table>
```

Each group gets its own `tbody`, so a row and its details stay together.
