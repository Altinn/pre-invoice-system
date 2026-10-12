# Data model: Kontrollgrunnlag før godkjenning

The schema changes are shown together here as one sketch. They ship as four migrations, V8–V11,
one per commit (see the plan's delivery order). Everything is additive except the swap of one
unique constraint (V8). Decisions are referenced as D1–D9 from [research.md](./research.md).

## V8–V11 sketch

```sql
-- D1: keep superseded usage rows; uniqueness is per import, not per period.
alter table bruksdata
    drop constraint bruksdata_periode_organisasjonsnummer_produkt_id_type_key,  -- verify name in MigrationIT
    add constraint bruksdata_unik_per_import unique (import_id, organisasjonsnummer, produkt_id, type);

-- D1: the run remembers the usage import it was built from.
alter table fakturakjoring
    add column bruksdata_import_id bigint references bruksdata_import;
update fakturakjoring k
   set bruksdata_import_id = (
       select min(b.import_id)
         from fakturalinje l
         join faktura f   on f.id = l.faktura_id
         join bruksdata b on b.id = l.bruksdata_id
        where f.kjoring_id = k.id);
-- Stays NULL for old runs with zero lines; views then say the import is unknown.

-- D9: customer name frozen at generation (NULL for rows generated before V11).
alter table faktura add column kundenavn text;

-- D2: which usage rows a blocking finding kept off the invoice.
create table kontrollfunn_bruksdata (
    kontrollfunn_id bigint not null references kontrollfunn,
    bruksdata_id    bigint not null references bruksdata,
    primary key (kontrollfunn_id, bruksdata_id)
);
create index kontrollfunn_bruksdata_bruksdata on kontrollfunn_bruksdata (bruksdata_id);

-- D6: the reconciliation of an exported LG04 against its run (one per run; export happens once).
create table lg04_avstemming (
    id                     bigint generated always as identity primary key,
    kjoring_id             bigint not null unique references fakturakjoring,
    eksportfil_id          bigint not null unique references eksportfil,
    antall_ordrer_fil      integer not null,
    sum_ore_fil            bigint  not null,
    antall_ordrer_kjoring  integer not null,
    sum_ore_kjoring        bigint  not null,
    antall_fakturaer       integer not null,
    sha256                 text    not null,   -- of the reconciled bytes; equals eksportfil.sha256
    avstemt_av             text    not null,
    avstemt_at             timestamptz not null default now(),
    check (antall_ordrer_fil = antall_ordrer_kjoring and sum_ore_fil = sum_ore_kjoring)
);
```

The `check` on `lg04_avstemming` makes the D3 guarantee structural: a mismatching reconciliation can
never be stored, so a stored row always means "matched".

## Entities

### Bruksdata (changed)

- Uniqueness is now per `import_id`. Rows of an AVVIST (superseded) import are kept, read-only.
- "The period's usage" = the rows of the period's VALIDERT import. There is at most one VALIDERT
  import per period, because confirming a new import marks all earlier non-AVVIST imports AVVIST
  (unchanged logic).

### Fakturakjøring (changed)

- New `bruksdata_import_id` (nullable for older runs without lines). Set in `generer` to the
  VALIDERT import whose rows were used.

**State machine (unchanged)**: GENERERT → GODKJENT → EKSPORTERT, and GENERERT/GODKJENT → FORKASTET.
The only new behaviour: export can now fail with `Lg04Avvik`. The run then stays GODKJENT, and no
files and no reconciliation row are written.

### Faktura (changed)

- New `kundenavn` (nullable). Display rule: `kundenavn` if present, else the current
  `kunde.virksomhetsnavn` + " (nåværende navn)".

### KontrollfunnBruksdata (new)

- Links a BLOKKERENDE finding (MANGLER_KUNDE, MANGLER_KUNDENUMMER or MANGLER_PRIS) to every usage
  row it kept off the invoice.
- A row has at most one link per run, because the generator `continue`s at the first failed rule.
- MANGLER_KONTERING and INAKTIV_AVTALE do **not** skip rows: those lines exist, but the run cannot
  be approved. So they have no links. The view shows those rows as invoiced and points to the
  findings list.

### Lg04Avstemming (new)

- Written only on a successful export, in the same transaction that sets EKSPORTERT.
- For runs exported before V9 there is no row; the same figures are computed on the fly from the
  archived file (D6) and not stored.

### Kjøringssammendrag (computed, not stored)

| Figure | Source |
|---|---|
| periode | `fakturakjoring.periode` |
| antall fakturaer | count of `faktura` |
| antall linjer | count of `fakturalinje` |
| antall LG04-ordrer | `EksportService.forventetAntallOrdrer` (the export's own assembly, D5) |
| sum beløp | Σ `fakturalinje.belop` |
| consistency | Σ `faktura.sum_belop` = Σ line `belop`; shown as a defect alert if not |

### Bruksdataavstemming (computed, not stored)

For each product (all active products, plus any inactive product with usage) × type:

| Column | Definition |
|---|---|
| importert rader | rows of the run's import |
| importert sum | Σ `antall` (BRUKSVOLUM) or Σ `belop` (cost types) of those rows |
| fakturert linjer | lines of the run whose `bruksdata_id` is in those rows |
| fakturert sum | Σ the lines' `antall` (volume) or `belop` (cost) |
| ikke fakturert | rows with no line, each with its `kontrollfunn` link |

**Invariant (SC-003)**: importert rader = fakturert linjer + ikke fakturert, for every
(product, type). A violation means a defect, and the view shows it as a danger alert rather than
hiding it.

### Fakturagrunnlag-regneark (computed)

Columns, in order:

| # | Column | Source |
|---|---|---|
| 1 | periode | run |
| 2 | ordre | `faktura.ordre_nr` |
| 3 | kundenummer | `faktura.kundenummer` (snapshot) |
| 4 | kundenavn | D9 display rule |
| 5 | organisasjonsnummer | `kunde.organisasjonsnummer` (cannot be edited) |
| 6 | fakturamottaker | `faktura.fakturamottaker_orgnr` (snapshot) |
| 7 | tilleggstekst | `faktura.tilleggstekst` (snapshot) |
| 8 | produkt | `produkt.kode` |
| 9 | type | `bruksdata.type` via `fakturalinje.bruksdata_id` |
| 10 | beskrivelse | line |
| 11 | servicekode | line |
| 12 | fakturareferanse | line (snapshot) |
| 13 | bestillingsnummer | line (snapshot) |
| 14 | antall | line — numeric |
| 15 | enhetspris | line — numeric |
| 16 | belop | line — numeric |
| 17 | bruksdata_id | line (traceability, T07) |
| 18 | pris_id | line (traceability, T07) |

Rows are ordered by (ordre, produkt, type), the same order as the LG04.
