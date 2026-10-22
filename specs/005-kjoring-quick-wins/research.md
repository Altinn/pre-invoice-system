# Research: A run page that says what to do next

## Findings (code, 2026-10-22)

- **R1: page structure.** `kjoring/detalj.html` renders, in order:
  1. breadcrumbs and title
  2. stopped-export alert
  3. summary
  4. e-mail figures (GODKJENT/EKSPORTERT)
  5. LG04 reconciliation
  6. blocking findings and warnings, one `ds-alert` each
  7. controls row (reconciliation link, XLSX/CSV)
  8. invoice table
  9. actions
  10. export files

  The actions sit after the invoice table.
- **R2: the period is a `LocalDate`.** `Fakturakjoring.periode` is a `LocalDate`, while
  `Periode.norskLabel()` exists ("januar 2027") and is used by the e-mail block. The run list,
  titles and breadcrumbs print the raw date.
- **R3: finding data.**
  - Findings are `Kontrollfunn(alvorlighet, kode, melding)`.
  - `alvorlighet` is `BLOKKERENDE` or `ADVARSEL`.
  - Codes repeat per customer, for example `AKTIV_KUNDE_UTEN_BRUK` once per customer with no usage,
    and `MANGLER_KUNDE` once per unknown orgnr.
- **R4: usage reconciliation.** `BruksdataavstemmingService.forKjoring(id)` returns
  `Bruksdataavstemming`, which offers:
  - `kilde()` (null for old runs)
  - `allFakturert()`
  - `invariantBrudd()` (a defect)
  - `ikkeFakturert()`, the rows explained by findings

  It is what the reconciliation page already computes.
- **R5: regenerate.** `FakturakjoringService.generer` refuses only when a non-FORKASTET run exists
  for the period. After Forkast, `POST /kjoringer` with `periode=ÅÅÅÅ-MM` works.
- **R6: who and when.**
  - A run has `generertAv/At` and `godkjentAv/At`.
  - Export time and user exist only on the stored LG04 reconciliation (`Lg04AvstemmingVisning.avstemtAv/At`,
    source `LAGRET`).
  - Older exports have none, so the panel says only "Eksportert".

## Decisions

- **D1: period label.** Add `public String periodeLabel()` to `Fakturakjoring`, delegating to
  `new Periode(periode).norskLabel()`.
  - Templates use `kjoring.periodeLabel()`.
  - The list shows the label with the first letter capitalised by CSS (`text-transform`). Text stays
    lower case for screen readers and search.
  - Rejected: a Thymeleaf `T()` static call, which Thymeleaf 3.1 restricts; formatting in every
    controller.
- **D2: findings groups.** A new record, `web.Funngruppe(String alvorlighet, String kode, List<String> meldinger)`.
  - `static List<Funngruppe> av(List<Kontrollfunn>)` groups by (alvorlighet, kode) and orders the
    groups: BLOKKERENDE before ADVARSEL, then count descending, then code.
  - Helpers: `antall()`, `blokkerende()`, `eksempel()` (the first message).
  - It lives in `web` because it is a view shape, not a domain concept. Unit-tested.
- **D3: panel markup.**
  - A `section.neste-steg` uses the design system's `ds-card` with `data-variant="tinted"` and an
    accent `data-color` for GENERERT and GODKJENT, and neutral otherwise.
  - The checklist is a `ul.sjekkliste`. Each item carries a state class (`ok`, `feil`, `advarsel`,
    `ukjent`) with a CSS icon from the DS colour tokens, and the state is also written in text, so
    colour isn't the only signal.
  - The actions keep their forms and dialogs from spec 004, moved into the panel.
- **D4: findings markup.** A `table.ds-table` with columns Alvorlighet (tag), Kode, Antall and
  Eksempel. Every group with more than one message gets a `details.ds-details` under its row,
  holding the full list.
  - Rejected: one `details` per group without a table. That loses the scan-by-column overview.
- **D5: Filer.** One section with:
  1. fakturagrunnlag XLSX and CSV (not for FORKASTET)
  2. the export-file table (when present)
  3. the links "Avstemming bruksdata" and "Kontroller arkivert LG04 på nytt" (the latter when
     exported)
- **D6: LG04 reconciliation result** (after export). It stays as a section after the invoices.
  - Its pass/fail headline also appears in the EKSPORTERT panel checklist, "LG04 stemmer med
    fakturagrunnlaget", so the check is seen first.
  - `EksportAvstemmingIT` asserts that string. It still matches.
- **D7: e-mail figures.** Shown in the EKSPORTERT panel only, under the existing heading "Til
  e-post til regnskap", with the copy button.
- **D8: controller.** `detalj()` additionally passes:
  - `funngrupper` and `antallBlokkerende`/`antallAdvarsler`, replacing the `blokkerende`/`advarsler`
    lists
  - `bruksdataavstemming`, only when the status is GENERERT, otherwise null
