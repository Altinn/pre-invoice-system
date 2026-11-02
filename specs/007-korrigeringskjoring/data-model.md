# Data model: Versioned correction runs

Proposed migration: `V12__korrigeringskjoring.sql`. Names remain Norwegian. Existing V1 rows and
ordinary flows are preserved.

## Changed: `bruksdata_import`

Extend the status check with `KANDIDAT` and `BEHANDLET`.

| Status | Meaning |
|---|---|
| `MOTTATT` | Existing scheduled fetch awaiting ordinary confirmation; no committed normalized rows |
| `VALIDERT` | Existing ordinary import used as the period's V1 source |
| `KANDIDAT` | Immutable complete candidate snapshot for comparison; normalized rows are stored |
| `BEHANDLET` | Candidate has at least one generated comparison/run; retained and reusable for a later baseline |
| `AVVIST` | Existing rejected/superseded ordinary or staged import semantics |

For candidate imports, `raadata_url`, `raadata_sha256` and `hentet_at` are mandatory. Add a partial
unique index on `(periode, raadata_sha256)` where status in (`KANDIDAT`, `BEHANDLET`). Candidate rows
use the existing `bruksdata` table and its per-import unique key.

`gjeldendeImport(periode)` continues to select only `VALIDERT`; candidates never replace it.

## Changed: `fakturakjoring`

| Column | Type | Rules / purpose |
|---|---|---|
| `type` | text not null default `ORDINAER` | `ORDINAER` \| `KORRIGERING` |
| `versjon` | integer not null default 1 | `>= 1`; unique with period |
| `forrige_kjoring_id` | bigint FK → `fakturakjoring`, null | previous immutable attempt/version in the period chain |
| `sammenligning_id` | bigint FK → `bruksdata_sammenligning`, null | mandatory for KORRIGERING |
| `korrigeringsaarsak` | text null | trimmed, non-blank for KORRIGERING |

Backfill all existing rows as `ORDINAER`, versioned by `(periode, generert_at, id)` so even historical
discarded attempts receive stable, non-reused versions. Existing non-discarded ordinary runs remain
the first visible delivery for their periods.

Constraints/indexes:

```sql
unique (periode, versjon)

check (
  (type = 'ORDINAER' and sammenligning_id is null and korrigeringsaarsak is null)
  or
  (type = 'KORRIGERING' and sammenligning_id is not null
   and length(btrim(korrigeringsaarsak)) > 0 and forrige_kjoring_id is not null)
)
```

Replace `en_aktiv_kjoring_per_periode` with:

```sql
create unique index en_paagaaende_kjoring_per_periode
  on fakturakjoring (periode)
  where status in ('GENERERT', 'GODKJENT');
```

Application rules additionally prevent a second non-FORKASTET ordinary run once an exported chain
exists. Earlier EKSPORTERT versions are intentionally allowed alongside one in-flight correction.

## New: `bruksdata_sammenligning`

One immutable diff header for a candidate against a specific exported baseline.

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | bigint identity PK | |
| `periode` | date not null | first day of month |
| `kandidat_import_id` | bigint not null FK → `bruksdata_import` | must have status KANDIDAT/BEHANDLET and same period |
| `grunnlag_kjoring_id` | bigint not null FK → `fakturakjoring` | latest EKSPORTERT version when comparison was made |
| `status` | text not null | `AAPEN` \| `KJORING_OPPRETTET` \| `ERSTATTET` \| `AVVIST` |
| `uendrede_rader` | integer not null | summary only |
| `opprettet_av` | text not null | audit-friendly snapshot |
| `opprettet_at` | timestamptz not null | |
| `erstattet_av_id` | bigint FK → same table, null | structural supersession link |

Unique `(kandidat_import_id, grunnlag_kjoring_id)`. A partial unique index allows at most one `AAPEN`
comparison per period.

## New: `bruksdata_endring`

One row per changed normalized natural key.

| Column | Type | Rules / purpose |
|---|---|---|
| `id` | bigint identity PK | |
| `sammenligning_id` | bigint not null FK | cascade is not used; history is retained |
| `organisasjonsnummer` | text not null | copied natural key |
| `produkt_id` | bigint not null FK → `produkt` | copied natural key |
| `type` | text not null | existing usage types |
| `endringstype` | text not null | `TILAGT` \| `ENDRET` \| `FJERNET` |
| `gammel_bruksdata_id` | bigint FK → `bruksdata`, null | present when the old state came directly from a source row |
| `ny_bruksdata_id` | bigint FK → `bruksdata`, null | present for added/changed candidate row |
| `gammelt_antall` / `nytt_antall` / `delta_antall` | numeric(14,2), null | volume triple |
| `gammelt_belop` / `nytt_belop` / `delta_belop` | numeric(12,2), null | cost triple |
| `avvist_av` / `avvist_at` / `avvist_aarsak` | nullable | explicit dismissal; reason required as a group |

Unique `(sammenligning_id, organisasjonsnummer, produkt_id, type)`.

Checks enforce:

- volume rows use only the antall triple; cost rows use only the belop triple;
- `old + delta = new`, treating a missing side as zero;
- delta is non-zero;
- TILAGT has no old value; FJERNET has no new value; ENDRET has both;
- dismissal columns are either all null or all present with non-blank reason.

For baselines after partial corrections, `gammel_bruksdata_id` may point to the last source row while
the frozen `gammelt_*` value represents the effective invoiced value after deltas. The full derivation
remains available through earlier selected change links.

## New: `korrigeringskjoring_endring`

Immutable selection made when a correction run is generated.

| Column | Type | Rules |
|---|---|---|
| `kjoring_id` | bigint FK → `fakturakjoring` | KORRIGERING only |
| `endring_id` | bigint FK → `bruksdata_endring` | same comparison as the run |
| `valgt_av` | text not null | |
| `valgt_at` | timestamptz not null | |

Primary key `(kjoring_id, endring_id)`. Unique `endring_id` among non-FORKASTET correction runs is
enforced in the service transaction/advisory lock because a partial unique condition cannot join the
run status. Stale-baseline validation is still mandatory.

## Changed: `fakturalinje`

Add:

| Column | Type | Purpose |
|---|---|---|
| `bruksdata_endring_id` | bigint FK → `bruksdata_endring`, null | source for a correction line |

Ordinary lines keep `bruksdata_id`; correction lines set `bruksdata_endring_id`. Service invariants
require exactly one source kind. A conservative SQL check can enforce `num_nonnulls(bruksdata_id,
bruksdata_endring_id) = 1` for newly created lines after legacy NULL cases are audited.

## Effective invoiced basis query

The service returns a map keyed by `(orgnr, produkt_id, type)`:

1. load V1's `bruksdata_import_id` rows;
2. load correction selections joined to EKSPORTERT runs for the period, ordered by `versjon`;
3. for each selection, set the key to its frozen new value, or remove it for FJERNET;
4. return both the effective value and its provenance chain.

The calculation happens in one read-only service and is covered by property-style reconciliation
tests. If measurements show it is too slow, a materialized baseline may be added later, but it is not
the source of truth.

## State transitions

```text
candidate import: KANDIDAT ──comparison/run──> BEHANDLET

comparison: AAPEN ──generate──> KJORING_OPPRETTET
                  ├─new candidate──> ERSTATTET
                  └─dismiss all──> AVVIST

run chain: V1 EKSPORTERT ──> V2 GENERERT ──> GODKJENT ──> EKSPORTERT
                                      └──────> FORKASTET
             latest exported version ──> next correction attempt (next unused version)
```

Only EKSPORTERT correction selections modify the computed effective basis.
