# Research: Small fixes from Økonomi's BF1 findings

Findings from the code (2026-10-12) and the decisions that follow. No schema change is needed.

## Findings

- **R1 (F06)**: `PrisversjonController.detalj` passes `produkter.alle()` (every product) to the page,
  while `PrisversjonService.aktiver` only requires prices for *active* products. The page asks for more
  than the rule needs.
- **R2 (F05)**: `FakturakjoringService.aktivVersjonFor` picks the single AKTIV version with
  `gyldig_fra ≤ periodens første dag ≤ gyldig_til` (til NULL = open). None or several → generation is
  refused. Activation refuses overlap with another AKTIV version. So the dates *are* a technical lock;
  it just isn't said anywhere in the UI. Consequence worth stating: a version starting mid-month
  (e.g. 2027-01-15) does not cover that month. A version that both starts and ends inside one month
  covers no period at all.
- **R3 (F03/F01)**: `ProduktService.alle()` orders by code only, and is shared by the price-version
  page and the kildenavn page. `produkt/liste.html` shows konto and artikkel-id but not dim 1/2/4.
  The edit page labels them "Dim 1/2/4".
- **R4 (C-09)**: `bygg()` already loads every customer (`kundeByOrgnr`) and every usage row. A set
  difference between active customers and the orgnrs in the usage gives the missing ones at no extra
  query cost. `kontrollfunn.kode` is free text (V4, no check constraint), so a new code needs no
  migration.
- **R5 (C-08)**: `GET /kjoringer/{id}/eksportfil/{filId}` loads the file by `filId` alone and never
  checks that it belongs to run `{id}`. It also logs nothing.

## Decisions

- **D1 (F06)**: `PrisversjonService.produkterFor(versjonId)` returns active products plus inactive
  products that have a price in this version, ordered by code. The page marks inactive ones. Pricing
  and activation rules stay as they are.
- **D2 (F05)**: `PrisversjonService.dekning(Prisversjon)` gives the covered periods as data:
  - the first covered period: the month of `gyldig_fra` if it starts on the 1st, else the next month
  - the last covered period: the month of `gyldig_til`, or open
  - empty if none

  The page renders it in words using `Periode.norskLabel()`. The list page gets a static help text
  under the date fields. This is computed from the same rule as `aktivVersjonFor` and is covered by
  tests, so the text can't drift from the behaviour.
- **D3 (F03)**: a new `ProduktService.oversikt()` orders active products first, then by code. Only the
  product list uses it, so other screens keep their order.
- **D4 (F01)**: a `@ConfigurationProperties("forsystem.produkt.dimensjoner")` record `Dimensjonsnavn
  (dim1, dim2, dim4)`, empty by default, with `etikett(n)` → "Dim n" or "Dim n (navn)". The environment
  variables are `PRODUKT_DIM1_NAVN`, `PRODUKT_DIM2_NAVN` and `PRODUKT_DIM4_NAVN`. Nothing is
  hardcoded (OQ-2, constitution I).
- **D5 (C-09)**: a new `Kontrollkode.AKTIV_KUNDE_UTEN_BRUK` (ADVARSEL, run-level, `faktura_id` null).
  It is raised in `bygg()` for every customer with `avtalestatus = AKTIV` whose orgnr has no row in
  the run's usage. It is ordered by orgnr, so it is deterministic.
- **D6 (C-08)**: `EksportService.lastNedEksportfil(kjoringId, filId)`:
  - refuses a file that belongs to another run, with the same message as an unknown id, so other
    runs' file ids aren't revealed
  - reads the file from the archive and logs `LASTET_NED` (type, file name)
  - returns the existing `Nedlasting` record

  The controller just streams it.
