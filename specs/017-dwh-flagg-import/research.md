# Research: flagged datavarehus export

## R1 — Can a 30 MB file be uploaded at all?
- **Finding**: no `spring.servlet.multipart.*` setting exists in `application*.yaml`, so Spring Boot's defaults
  apply (1 MB per file / 10 MB per request). The real export is 30 566 590 bytes; it would be refused with
  `MaxUploadSizeExceededException` before reaching our code. Prod sits behind Container Apps ingress; no
  body-size limit has been checked there (verify in tt02).
- **Decision**: set `max-file-size` and `max-request-size` to 100 MB (headroom for 12 months ≈ 120 MB is
  *not* needed: one upload is ≤ 3 months). Add a friendly error page text for the exceeded case via the
  existing `GlobalExceptionHandler`.
- **Alternatives**: ask the DWH for a per-month file (cheaper, but the file already exists in this shape);
  gzip upload (extra step for users).

## R2 — Memory and speed with 113 k rows
- **Finding**: `DwhCsv.tabell` builds `List<List<String>>` then a `HashMap` per row (~14 entries) — an order of
  magnitude over the file size — and runs twice per request (`les` and `kunder`). The controller keeps the
  upload as `byte[]` in the HTTP session (`DWH_FIL`) between preview and confirm.
- **Decision**: one streaming pass producing compact `DwhRad`s only for BRUKT rows and counters for the rest;
  reader result reused for rows, customers and report. Keep the session-held `byte[]` (few concurrent users,
  needed for archiving on confirm); revisit if the container heap proves too small. Measure parse time
  against the real file in the quickstart.
- **Alternatives**: archive at preview time and keep only a reference in the session (risks orphan archives);
  temp file on disk.

## R3 — What does `regel` mean and can we rely on it?
- **Finding** (from the file): tokens `orgnr=…`, `navn=…` describe how identity was resolved;
  `inkludert=positiv_mengde_og_aktiv_crm` ⇔ flag_altinn 1 (60 096 rows, exact match);
  `inkludert=dpv_ressurs` + `ekskludert_altinn=dpv_ressurs` ⇔ flag_dpv 1 (8 897); `ekskludert=…` on the rest.
  Resolution sources vary per `source_table` (`creator_akronym`, `senderorgnr`, `service_owner_org_number`…).
- **Decision**: inclusion follows the flags only; `regel` is evidence — grouped and shown, never parsed for
  decisions (FR-013). Unknown tokens pass through. Contract question → OQ-25.
- **Alternatives**: derive inclusion from `regel` (brittle, undocumented vocabulary); recompute inclusion
  from CRM status ourselves (duplicates the DWH, conflicts with OQ-4 «second net»).

## R4 — Repeated day rows (2 298 groups)
- **Finding**: e.g. NAV SMS Norge 2026-08-03 has one row with quantity NULL/flag 0 and one with 172 206/flag 1
  for the same key — the group is a zero/NULL placeholder plus the real row, not a double count. Other
  groups (to be verified in the fixture) may be real duplicates.
- **Decision**: sum used rows; unused placeholders don't count. Whether true duplicates exist among *flagged*
  rows is checked in a test on the real file and raised as OQ-27 if non-zero.

## R5 — Flagged rows with no fakturareferanse (3) and DPV rows with kundenummer (3 009)
- **Decision**: unchanged cross-check behaviour; the 3 flagged rows flow to the MANGLER_REFERANSE control
  (specs/012). DPV rows are reported only.

## R6 — Fixture
- **Decision**: cut ≈ 300 rows from the real file with a script kept in `src/test/resources/dwh/README.md`
  (selection rules, not the data): at least one row of every (product, flag_altinn, flag_dpv, regel) combination,
  every orgnr anomaly (`NULL`, empty, `unknown`, `owner`, name), urn-prefixed and plain resources, an appinfra
  amount row, a NULL-quantity flagged-0 row, rows in all three months, a repeated-key group. Organisation
  numbers and names are public register data; kundenummer/fakturareferanse kept as they are test-only and
  not personal. Expected totals are computed independently in the test from the fixture.
