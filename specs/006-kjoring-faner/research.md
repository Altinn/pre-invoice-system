# Research: Run page in tabs, with progress and invoice search

## Findings (code, 2026-10-22)

- **R1: who and when.** The run records `generertAv/At` and `godkjentAv/At`. Export and discard
  appear only in the audit log:
  - `Handling.EKSPORTERTE` and `FORKASTET` on entity `FAKTURAKJORING`, with id = the run id
  - plus `LASTET_NED`, `EKSPORT_STOPPET`, `GENERERTE` and `GODKJENTE`

  `HendelsesloggRepository` has only a global top-200 finder.
- **R2: invoices.** `Faktura` holds `ordreNr`, `kundenummer`, `kundenavn` (frozen; null on old runs),
  `fakturamottakerOrgnr` and `sumBelop`. `FakturakjoringService.kundenavn(fs)` resolves the shown
  name, including old runs. Production has about 300 invoices per run, so in-memory filtering is
  trivial.
- **R3: summary cost.** `sammendrag(id)` loads the invoices and their lines. Per run on the list
  that is about 12 calls a year, which is fine.
- **R4: Designsystemet.**
  - Sortable headers style `th[aria-sort] > button`.
  - Pagination is `<ds-pagination class="ds-pagination" data-current data-total data-href="…%d">`
    (designsystemet-web). It renders the links, the ellipsis and Norwegian labels.
  - Badge: `<span class="ds-badge" data-count data-color>`.
  - There is no stepper and no link-tab component.
- **R5: tests.** Two ITs request content that moves:
  - `BruksdataavstemmingIT` requests `/avstemming`.
  - `KjoringNesteStegIT` reads findings and Filer from the run page.

## Decisions

- **D1: tabs are pages.** Each tab is its own GET route and template, rendered in full.
  - The header comes from the fragment `kjoring/fragmenter :: hode(fane)`.
  - Tab bar: `nav.faner[aria-label="Kjøring"] > ul > li > a[aria-current="page"]`, styled like the
    main menu with tokens. It scrolls sideways within itself on narrow screens.
  - Navigation uses links, not `role=tablist`, because each tab is a separate page.
  - Rejected:
    - `ds-tabs` with client-side panels: one heavy page, no address per tab.
    - htmx partial swaps: more moving parts. Boosted links already make page changes smooth.
- **D2: header data.** A private `fane(id, model)` in the controller adds to the model the run, the
  invoice count, the blocking and warning counts, and the status. Every tab calls it.
- **D3: plain status labels.** `fragmenter :: status(s)` maps each status to a tag colour and text
  with `th:switch`. It is shared by the header and the list. No Java change.
- **D4: progress strip.** `Fremdrift.av(Fakturakjoring, List<Hendelseslogg>)` builds four `Steg`
  entries `(navn, tilstand FERDIG|NAA|SENERE, hvem, naar)`, plus `forkastet` (who and when) when
  the status is FORKASTET.
  - Export who/when comes from the latest EKSPORTERTE event, and discard from the latest FORKASTET
    event.
  - If an event is missing, the step is still done, but without who and when.
  - Step 4, "Sendt til regnskap", is never done. It is NAA once exported.
- **D5: invoice list.**
  `Fakturautvalg.av(List<Faktura>, Map<Long,String> navn, String q, String sorter, int side)` does:
  - **Search**: trim, lower-case, then match "contains" on name, mottaker orgnr, kundenummer and
    order number.
  - **Sort**: `ordre` (default, ascending), `kunde` (A–Z by name, Norwegian collation), `belop`
    (descending).
  - **Paging**: 50 per page, with the page clamped to 1…last.

  It returns `rader` (each with its invoice and name), `antallTreff`, `sumTreff`, `side`,
  `antallSider`, `q` and `sorter`.
  - The search form is a GET form, boosted by htmx. The search input also triggers a debounced
    `hx-get` that swaps only the result area (`hx-select`) and pushes the address (FR-009).
  - Sort headers are links that keep `q` and reset the page to 1. They carry `aria-sort` and are
    styled like the DS sort buttons.
- **D6: Kontroller.** It holds:
  - the grouped findings (005)
  - the usage reconciliation (`id="bruksdata"`), the former `avstemming.html` body
  - the LG04 result with the re-check link, when present

  `GET /{id}/avstemming` redirects to `/{id}/kontroller#bruksdata`. `avstemming.html` is deleted.
  The Oversikt checklist links point at the Kontroller anchors.
- **D7: Historikk.** `GET /{id}/historikk` lists
  `hendelser.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc("FAKTURAKJORING", id)`.
- **D8: run list.** `Kjoringsrad(Fakturakjoring, int antallFakturaer, BigDecimal sum, long blokkerende, long advarsler)`
  is built in the controller from `sammendrag(id)` and `funn(id)`.
- **D9: test convention.** `TemplateKonvensjonTest` skips `layout.html` and any template named
  `fragmenter.html`. Those hold only fragments, never a whole page.
