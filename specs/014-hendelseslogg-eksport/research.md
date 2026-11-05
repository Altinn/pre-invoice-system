# Research: The complete event log, filtered and exported

## D1 — Dynamic SQL instead of derived repository methods

**Decision**: `NamedParameterJdbcTemplate` with a `where` built from the non-empty filters.

**Rationale**: Four optional filters would need sixteen derived methods; Spring Data JDBC has no criteria
API on repositories without extra setup. The SQL is small, parameterised, and read-only.

## D2 — Offset paging, 50 rows

**Decision**: `order by tidspunkt desc, id desc limit 50 offset (side-1)*50` plus `count(*)`.

**Rationale**: At a few thousand rows a year offset paging is instant; keyset paging would complicate
links for no gain. No index added (a migration would also clash with specs/013's V15).

## D3 — Oslo days

**Decision**: `fra` → `tidspunkt >= fra 00:00 Europe/Oslo`, `til` → `tidspunkt < (til + 1) 00:00 Europe/Oslo`.
Display and files use Europe/Oslo.

**Rationale**: Users think in Norwegian dates; both days inclusive matches «fra 01.10 til 31.10».

## D4 — Readable details

**Decision**: Parse with Jackson; objects flatten to `a.b.c: v` pairs joined by `; `, arrays of scalars
join with `, `, arrays of objects render each element in `{…}`. `periode` is lifted to its own column when
the top-level object has it (`2027-01` or `2027-01-01` → `2027-01`). The raw JSON stays in the last column.

**Rationale**: Excel cannot read jsonb; one readable text column is filterable with «Inneholder», and the
period column covers the most common question («what happened for January?»).

## D5 — Spreadsheet improvements in RegnearkEksport

**Decision**: `LocalDateTime` values become date-time cells (`yyyy-mm-dd hh:mm:ss`) in XLSX and
`yyyy-MM-dd HH:mm:ss` text in CSV. Every XLSX gets an autofilter on the header row and a frozen header.

**Rationale**: Økonomi asked to sort and filter; the autofilter puts the dropdowns there from the start.
It changes no values in the existing downloads.

## D6 — Download audit

**Decision**: `LASTET_NED`, entity `HENDELSESLOGG`, entity id null, details `{format, antall, filter…}`.

**Rationale**: Every other download is logged; the log of who exported the log belongs in the log.
