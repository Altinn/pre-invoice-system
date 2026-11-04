# Research: Invoice texts per invoicing run

## R1 — Store the texts as columns on `fakturakjoring`

- **Decision**: Three nullable `text` columns — `topptekst`, `bunntekst`, `var_referanse` — with
  `char_length` checks (120/120/25) and a check that all three are set while the run is GENERERT or
  GODKJENT.
- **Rationale**: One value each per run, read on every export; a side table adds a join and a
  lifecycle for nothing. The checks make the database refuse an over-long text or an exportable run
  without texts even if the application has a bug.
- **Alternatives**: A `fakturakjoring_tekst` key/value table (more flexible, no need yet); NOT NULL
  columns with a backfill for every run (would claim exported runs were written with texts they
  were not — see R2).

## R2 — Backfill only runs that can still be exported

- **Decision**: V13 sets the defaults on GENERERT and GODKJENT runs. EKSPORTERT and FORKASTET runs
  keep NULL, and the page says the texts were not stored.
- **Rationale**: Exported files carried the per-invoice link and the configured responsible person;
  writing today's defaults onto those runs would misstate history (constitution III). Production
  has no runs yet (first run February 2027); tt02 has test runs.
- **Header in SQL**: the Norwegian month name comes from a `case extract(month …)` in the migration,
  matching `Periode.norskLabel()` («januar 2027»). Locale-dependent `to_char(…, 'TMMonth')` is
  avoided because it depends on the server's `lc_time`.

## R3 — A `Fakturatekster` value object owns defaults, limits and validation

- **Decision**: `billing.Fakturatekster(topptekst, bunntekst, varReferanse)` with
  `standard(Periode)`, `normalisert()` (trim), `valider()` throwing `Regelbrudd`, and constants for
  the LG04 widths and the advisory printed limits (`TODO(OQ-23)`).
- **Rationale**: Generation, editing and the template need the same numbers; one place keeps them
  consistent and unit-testable without a database.
- **Validation**: after trim — non-empty; `length() <= limit` (Windows-1252 is single-byte, so
  characters = bytes once the charset check passes); no `Character.isISOControl` (line break, tab
  would break the fixed-width line); every character `canEncode` in Windows-1252 (otherwise the
  writer would emit `?`). The message names the field («Topptekst», «Bunntekst», «Vår referanse»)
  and the reason, and for an unencodable character, the character.

## R4 — Edit and approval serialise on the run row

- **Decision**: `FakturakjoringRepository.finnForOppdatering(id)` (`select … for update`); both
  `godkjenn` and `endreTekster` read the run through it.
- **Rationale**: Both save the whole record (Spring Data JDBC). Without a lock, an edit that read
  GENERERT could commit after approval and write GENERERT back (un-approving the run), or approval
  could write stale texts over a just-saved edit. With the lock, whichever comes second sees the
  first's result: an edit after approval is refused (FR-007).
- **Alternatives**: A conditional `update … where status = 'GENERERT'` for the texts only (fixes one
  direction, not approval overwriting texts); optimistic `@Version` (needs a new column and retry
  handling for a once-a-month form).

## R5 — Audit as one `ENDRET` row per save

- **Decision**: `Handling.ENDRET` on `FAKTURAKJORING`, detaljer `{periode, versjon, endringer:
  {<felt>: {fra, til}}}` with only the changed fields; no row when nothing changed.
- **Rationale**: Reuses the generic verb registry edits use; the Historikk tab already shows the
  run's rows with their JSON details.

## R6 — The export reads the run, configuration goes

- **Decision**: `EksportService` builds every order with the run's three texts (Vår referanse in
  both responsible fields). `eksporter()` refuses a run without texts. The counting/reconciliation
  paths for old exported runs tolerate NULL (they compare order counts and sums, not texts).
  `EksportKonfig` (`ansvarlig1/2`, `fakturaUrlBase`) is deleted: nothing else reads it and no
  environment sets it.
- **Writer**: `responsible`/`responsible2` are written with the same pad-or-cut helper as the text
  fields (25), so even a value that bypassed validation cannot spill into the next field. FEL's
  sample (21 characters) renders identically.

## R7 — UI on the Oversikt tab, between "Neste steg" and "Sammendrag"

- **Decision**: A "Tekster på fakturaen" section. GENERERT: a form with three text fields
  (`maxlength` = LG04 width), help text per field with the LG04 limit and, for header and footer,
  regnskap's suggested printed limit marked unconfirmed (OQ-23), and a warning when the stored text
  is above it. Other statuses: the same three values read-only, or "ikke lagret" for old runs. A
  refused save returns to the page with the error and the submitted values kept.
- **Rationale**: The texts are checked and approved together with the run, so they sit on the page
  where approval happens, above the approve button's context. The approval dialog repeats nothing —
  the section is visible right above it.
