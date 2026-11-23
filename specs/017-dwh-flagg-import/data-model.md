# Data model: flagged export

## New table `bruksdata_kilde` (Flyway V16)

Grouped source summary of one committed import (spec FR-008). Rows are insert-only; a superseded import keeps
its rows (constitution III).

| Column | Type | Notes |
|---|---|---|
| id | bigint identity PK | |
| import_id | bigint not null → bruksdata_import | |
| organisasjonsnummer | text not null | same value as the usage row |
| produkt_id | bigint not null → produkt | |
| type | text not null | `BRUKSVOLUM` / `AZURE_KOSTNAD` / `SMS_KOSTNAD`, as in bruksdata |
| kildetabell | text null | DWH `source_table` |
| ressurs | text null | `resource_id` without `urn:altinn:resource:` |
| antall_dagsrader | integer not null check (> 0) | day rows that contributed |
| antall | numeric null | summed quantity (volume types) |
| belop | numeric(14,2) null | summed amount (cost types), øre-rounded like bruksdata |
| regel | text[] not null default '{}' | distinct `regel` texts, verbatim |

Constraint: `unique (import_id, organisasjonsnummer, produkt_id, type, kildetabell, ressurs)` with nulls
treated as equal (`nulls not distinct`, PostgreSQL 15+). Invariant tested: per (import, orgnr, produkt, type)
the sum of `antall`/`belop` equals the matching `bruksdata` row. Index on `(import_id, organisasjonsnummer)`.

No change to `bruksdata`, `bruksdata_import` or `produkt_kildenavn`.

## In-memory (not persisted)

- `DwhRad` + `flaggAltinn: Boolean`, `flaggDpv: Boolean`, `kildetabell`, `ressurs` (normalised), `regel`,
  `orgnrGyldig` derived. Null flags ⇒ legacy file ⇒ class BRUKT.
- `DwhRadklasse`: `BRUKT`, `UTENFOR_PERIODE`, `DPV`, `EKSKLUDERT`.
- `DwhFilrapport`: `totalt`, `perKlasse` (count), `utenforPerPeriode` (YearMonth → count),
  `volumPerProdukt` (class × product name → sum), `ekskludertPerArsak` (regel reason text → count/volum),
  `ekskludertPerOrg` (orgnr or «ukjent» → volum, top N).
- `DwhKildegruppe` mirrors a `bruksdata_kilde` row before commit.
- `Forhaandsvisning` gains `filrapport` and `kildegrupper` (both nullable → older paths unchanged).

## Validation rules (reader/classifier)

| Condition | Result |
|---|---|
| flag column value ∉ {0,1} (or blank) in a flagged file | rejected row, line + value |
| BRUKT row: orgnr not 9 digits | rejected row (as today) |
| BRUKT row: quantity negative / non-numeric / fractional | rejected row (as today) |
| BRUKT row: volume product with NULL quantity | counted as 0 |
| BRUKT row: product name unmapped | one reject per (orgnr, name) (as today) |
| non-BRUKT row with any of the above defects | ignored, never an error |
| flagged file, no BRUKT rows in period | blokkering |
