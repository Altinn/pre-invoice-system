# Research: Remove the customer registry (specs/018)

Findings from reading the code at `64f93ba` (main), and the decisions they lead to.

## R1 — What the registry feeds today

`FakturakjoringService.bygg` loads `kunde`, `kundenummer_regel` and `kunde_referanse` in full
(`:245-250`) and uses them for:

| Use | Where | After this feature |
|---|---|---|
| orgnr → customer, MANGLER_KUNDE | `:272-274, 324-327` | gone; no customer means no kundenummer (R4) |
| kundenummer rule (per product, servicekode, tilleggstekst), MANGLER_KUNDENUMMER | `:281, 329-332, 854` | the import's kundenummer; MANGLER_KUNDENUMMER when it is blank |
| INAKTIV_AVTALE | `:341-344` | gone (spec clarification) |
| AKTIV_KUNDE_UTEN_BRUK | `:348-358` | gone (spec clarification) |
| reference (product-specific or default) + bestillingsnummer fallback | `:311, 865, 943-951` | the import's fakturareferanse/bestillingsnummer, fallback kept |
| invoice grouping (kunde_id, kundenummer) | `:317` | one invoice per orgnr (FR-009) |
| name, tilleggstekst, fakturamottaker snapshot | `:412-416` | name from the import; tilleggstekst NULL; mottaker = orgnr |
| threshold per kunde_id | `:367-385` | per orgnr |
| NY_KUNDE / STORT_AVVIK per kunde_id | `:480-496` | per orgnr |

Live reads after generation: `kundenavn()` `:746-757` (fallback to the current registry name for invoices
made before V11), `utenFakturareferanse` `:708` and `grunnlagslinjer` `:782` (orgnr via `kunder.findById`
because `faktura` has no orgnr). All three must read the invoice's own columns (FR-013).

## R2 — Where the DWH customer values live today

- Flagged CSV: `DwhCsv.Leser` collects a `KundeIFil` per orgnr from the **first** used row
  (`putIfAbsent`, `:195, :256`). Conflicts between rows are silently ignored.
- API: `DwhRespons.les` reads `kundenummer` and `fakturareferanse` only; `organisation_name` and
  `bestillingsnummer` exist in the view (specs/001 research) but are not read.
- `DwhRad` carries kundenummer/fakturareferanse, documented as «cross-check only».
- Nothing is persisted. Only the archived raw payload keeps them.
- forsystem's own CSV format (`CsvUploadUsageSource`, columns `periode, organisasjonsnummer,
  produktkode, type, antall, belop`) has **no** customer columns.

### Decision D1 — store per import, per orgnr

New insert-only table `bruksdata_kunde (import_id, organisasjonsnummer, …)`, written in the same
transaction as `bruksdata` and `bruksdata_kilde` by `UsageImportService.importer`. Generation reads the
rows of the run's import (`fakturakjoring.bruksdata_import_id`), so customer values and usage always
come from the same import (spec edge case «several imports for the same period»).

- *Rationale*: mirrors `bruksdata_kilde` (specs/017): same lifecycle, same superseded-import rule,
  traceable to the archived file (constitution III).
- *Alternatives*: columns on `bruksdata` (repeats values per product row and needs per-row conflict
  logic); read them from the archived raw file at generation (slow, re-parses 30 MB, mixes import
  formats into billing).

### Decision D2 — conflicts are recorded at import, blocked at generation

The reader collects every distinct non-blank kundenummer, fakturareferanse and bestillingsnummer per
orgnr over the **used** rows. One value → stored. Several → the first (file order) is stored and the
full list goes into `motstridende` (text[], e.g. `kundenummer: 5663 | 5664`). Generation turns a
non-empty `motstridende` into a new blocking finding `MOTSTRIDENDE_KUNDEDATA` and puts none of the
organisation's usage on an invoice (FR-008). Blank values are not a conflict: rows with and without a
fakturareferanse give the one non-blank value. (Profiling: 3 flagged rows lack fakturareferanse,
specs/017.)

- *Alternatives*: refuse the import (one bad CRM record would stop the whole month's usage, and the
  preview already has an all-or-nothing rule for row errors, which this is not); pick one silently
  (what happens today; that is inventing a value).
- The preview shows the conflicts as a plain import fact («2 organisasjoner har motstridende kundedata
  i fila»). That is a DWH data error, not a comparison with forsystem, so FR-002 holds.

### Decision D3 — forsystem's own CSV gets four optional columns

`kundenavn, kundenummer, fakturareferanse, bestillingsnummer` become optional columns in
`docs/csv-format.md`. A file without them imports as today, and every organisation then gets
MANGLER_KUNDENUMMER at generation. Within one file, the conflict rule from D2 applies.

- *Rationale*: the own CSV format is the documented fallback when the DWH is down (OQ-4) and what the
  demo profile and most ITs use. Without customer columns it could never produce an approvable run.
  The values are copied from the DWH/CRM export. This is a usage file, not a registry: nothing is
  kept beyond the import, and it cannot be edited in forsystem.
- *Alternative*: drop the own format for billing and use only DWH exports as fallback. Simpler, but
  every billing IT and the demo data would have to move to the DWH format in the same change. **Check
  with Økonomi** that a hand-made fallback file with customer columns is acceptable. If not, D3
  becomes «the own format is for tests and demo only».

### Decision D4 — API reads name and bestillingsnummer too

`DwhRespons.les` also reads `organisation_name` and `bestillingsnummer` (both nullable). `DwhRad` gets
`navn` and `bestillingsnummer`. Missing fields remain null (no invented value).

## R3 — The invoice's link to the customer

`faktura.kunde_id` and `faktura_under_terskel.kunde_id` are NOT NULL FKs to `kunde`, part of
`unique (kjoring_id, kunde_id, kundenummer)`. `faktura` has no orgnr column. `faktura.kundenavn` (V11) is
NULL for invoices made before 2026-10 and falls back to the live registry name.

### Decision D5 — orgnr on the invoice, then drop the registry (V17 + V18)

Two migrations: V17 does steps 1–2 and makes `kunde_id` nullable; V18 does steps 3–4 once no code reads the registry:

1. `faktura` adds `organisasjonsnummer`, backfilled from `kunde`; `kundenavn` backfilled where NULL.
   Both then become NOT NULL.
2. `faktura_under_terskel` already has orgnr and name.
3. Drop `kunde_id` from both tables (with their uniques). New uniques: `faktura (kjoring_id,
   organisasjonsnummer, kundenummer)`, which keeps old runs that had several kundenummer per orgnr
   valid, and `faktura_under_terskel (kjoring_id, organisasjonsnummer, kundenummer)`.
4. Drop `kunde_referanse`, `kundenummer_regel`, `kunde`.

- *Rationale*: after step 1, every historical invoice carries everything it showed (FR-012). Every
  registry change since V2 is in `hendelseslogg.detaljer` (entities `KUNDE`, `KUNDE_REFERANSE`,
  `KUNDENUMMER_REGEL`), which stays readable. Keeping dead tables invites someone to read them again.
- *Alternative*: keep the tables renamed `arkiv_*`. Rejected: no reader, and the event log already
  holds the history. **Before deploying to tt02, take a `pg_dump` of the three tables** (runbook step).
- `kunde.fakturamottaker_orgnr` is not needed. It already sits in `faktura.fakturamottaker_orgnr` as a
  snapshot.

## R4 — Controls

`Kontrollkode` is an enum. Stored findings keep their `kode` as text and are rendered through the enum
(`kontroller.html:29,56`, `hjelp/fragmenter.html:106`).

### Decision D6 — retire codes without deleting them

`MANGLER_KUNDE`, `INAKTIV_AVTALE` and `AKTIV_KUNDE_UTEN_BRUK` stay in the enum, marked retired
(`@Deprecated` plus a flag `utgatt = true`), so old runs render with their texts. Generation no longer
produces them. The help lists them under «Utgåtte kontroller» with a one-line note. The build check
(specs/016) still requires a «Slik løser du det» text: for these it says the control no longer runs.

- `MANGLER_KUNDENUMMER`: new message «Datavarehuset har ikke kundenummer for <orgnr> (<navn>)». It is
  raised once per orgnr, not per orgnr/product, and links the skipped usage rows as today
  (`kontrollfunn_bruksdata`).
- `MOTSTRIDENDE_KUNDEDATA` (new, BLOKKERENDE): lists the values. It also links the skipped rows.

## R5 — Things that disappear with the registry

`KundeService`, `KundeController` + `templates/kunde/detalj.html`, `Kunde*` entities and repositories,
`Avtalestatus`, `BrregOppslag` + both implementations (they only served `KundeService`; check there is
no other caller), `DwhImportService.manglendeKunder/opprettManglendeKunder/kryssjekkKunderegister`, the
POST `/bruksdata/last-opp/opprett-kunder` route and its template blocks, `Demodata` customer seeding.
Config keys used only by BRREG are removed from `application.yaml` and `.env.example`.

## R6 — Tests

- `BillingFixture` (used by ~18 billing ITs) writes `Kunde` + rules + references. It is rewritten to
  write an import with `bruksdata_kunde` rows. Most ITs then only need a new helper call. ITs that test
  product-specific references, several kundenummer, tilleggstekst, fakturamottaker or the three
  retired controls are deleted or rewritten to the new rules.
- `KundeServiceIT`, `KundeBrregIT`, registry parts of `RegistryRepositoryIT`: deleted.
- `DwhFilImportIT`, `DwhFlaggImportIT`: the create-customers flow becomes «the route returns 404 and
  the preview has no customer notice».
- LG04 golden files must stay byte-identical (SC-004). Their fixtures already use one kundenummer per
  customer and default references; verify, and adapt only the fixture setup, never the golden file.
- Migration IT: a database at V16 with customers, invoices (incl. NULL `kundenavn`) and threshold
  rows migrates to V17 with names and orgnr filled in.
