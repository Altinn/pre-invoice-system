# Feature Specification: The complete event log, filtered and exported

**Feature Branch**: `claude/014-hendelseslogg-eksport`

**Created**: 2026-11-05

**Status**: Draft — ready for planning

**Input**: Økonomi's kickoff list, row 24: «Hendelseslogg: Tilgjengeliggjøre logg utover siste 200 endringer,
inkl. excel eksport». Action items: «Bør være mulig å hente ut komplett logg» and «Muliggjøre uthenting av
logg til excel (for å enklere kunne sortere og filtrere på hendelser)».

## Context

`/hendelseslogg` shows the 200 newest rows of `hendelseslogg` (`findTop200ByOrderByTidspunktDescIdDesc`),
with the time as raw UTC text and the details as raw JSON. Older rows cannot be seen in the app at all.
Each row has a time, a user, an action (`handling`), an entity type and id, and free-form details (jsonb).
Usernames are readable for new rows (specs/010 FR-011). The run pages already show a run's own history
(Historikk tab); that tab is unchanged.

## Clarifications

### Session 2026-11-05 (design decisions settled in this spec)

- Q: Which filters? → A: Time range (from date, to date, inclusive, Norwegian time), user (part of the
  name, case-insensitive), entity type and action (both chosen from the values in the log). Filters
  combine with AND. Entity id is not a filter: the run's Historikk tab already covers «one entity».
- Q: How much does a download contain? → A: Every row that matches the filters, in the same order as the
  page, regardless of paging. With no filters that is the complete log, as Økonomi asked.
- Q: How are the jsonb details made readable in Excel? → A: A column `detaljer` with the details as
  «nøkkel: verdi» pairs separated by «; » (nested values as `endringer.topptekst.fra: …`, lists comma
  separated), a column `periode` lifted out of the details when present (so the log can be filtered per
  month), and the raw JSON in a last column for completeness.
- Q: Which time zone? → A: Europe/Oslo, on the page, in the filters and in the files. The page showed UTC
  before, which is an hour or two off.
- Q: Is the download itself logged? → A: Yes, as `LASTET_NED` on entity `HENDELSESLOGG` with the filters,
  the format and the row count, like every other download.
- Q: Who may see and download it? → A: Any signed-in user (LESER and up), as the page today.
- Q: Page size? → A: 50 rows per page, newest first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Page through the whole log and filter it (Priority: P1)

Økonomi wants to know who changed the prices in October. They open Hendelseslogg, pick «fra 01.10.2026
til 31.10.2026» and entity `PRIS`, and get every matching row, 50 per page, with «Viser 1–50 av 132».

**Why this priority**: Today nothing older than the 200 newest rows is visible.

**Independent Test**: With 260 rows in the log, page 6 shows rows 251–260; a filter on user narrows
the count and paging keeps the filter.

**Acceptance Scenarios**:

1. **Given** more than 50 rows, **Then** the page shows the newest 50, the total, and links to the
   next/previous page; the last page shows the oldest rows.
2. **Given** a from/to date, **Then** only rows with a Norwegian-time date in that range are shown (both
   days included).
3. **Given** part of a username in any case, **Then** only that user's rows are shown.
4. **Given** an entity type or action chosen from the lists, **Then** only those rows are shown.
5. **Given** several filters, **Then** they combine; paging links and the download links keep them.
6. **Given** no matches, **Then** the page says so and offers to clear the filters.
7. **Given** a from date after the to date, **Then** the page says so and shows nothing.
8. **Given** the page, **Then** times are Norwegian time and the details read as «nøkkel: verdi».

---

### User Story 2 - Download the log to Excel (Priority: P1)

Økonomi downloads the complete log (or the filtered one) as XLSX to sort and filter it in Excel.

**Why this priority**: Økonomi's explicit wish.

**Independent Test**: Download with and without a filter: the row count matches the page's total, the
XLSX has a filter on the header row, times are Excel date-times, and the details are readable.

**Acceptance Scenarios**:

1. **Given** no filter, **When** XLSX is downloaded, **Then** it holds every row of the log, newest
   first, with columns tidspunkt, bruker, handling, entitet, entitet_id, periode, detaljer, detaljer_json.
2. **Given** filters, **Then** the file holds exactly the filtered rows (all pages).
3. **Given** the XLSX, **Then** tidspunkt is a date-time cell, entitet_id a number, the header row has
   an autofilter and stays visible when scrolling.
4. **Given** CSV, **Then** it has the same columns, times as `yyyy-MM-dd HH:mm:ss`.
5. **Given** any download, **Then** a `LASTET_NED` row on `HENDELSESLOGG` records format, filters and
   count; the file name says what it holds (`hendelseslogg-<dato>.xlsx`, `-filtrert` when filtered).

### Edge Cases

- A row without details: `detaljer` and `detaljer_json` are empty.
- Details with nested objects or lists (invoice text changes, finding ids) flatten as described.
- A filter value that is not in the lists (typed into the URL) simply matches nothing.
- Very large logs: the download holds everything; at today's volume (a few thousand rows a year) this is
  small. Paging uses offset; fine at this volume.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The page MUST show the whole log, 50 rows per page, newest first, with the total count.
- **FR-002**: The page MUST filter by from/to date (Norwegian time, inclusive), user (contains,
  case-insensitive), entity type and action, combined.
- **FR-003**: Paging and download links MUST keep the filters.
- **FR-004**: The page and files MUST show times in Europe/Oslo and details as readable text.
- **FR-005**: The log MUST be downloadable as XLSX and CSV, all matching rows, columns as in US2.
- **FR-006**: The XLSX MUST have typed cells (date-time, number), an autofilter and a frozen header row.
- **FR-007**: Each download MUST be logged (`LASTET_NED`, entity `HENDELSESLOGG`, filters, format, count).
- **FR-008**: The user guide and the feature list MUST describe the page and the download.

### Key Entities

- **Hendelseslogg**: unchanged (no migration). A filter value object and a readable-details rendering
  are added in code.

## Success Criteria *(mandatory)*

- **SC-001**: 100 % of the log is reachable from the page and in one download.
- **SC-002**: Økonomi can sort and filter the downloaded log in Excel without editing it first.

## Assumptions

- «Komplett logg» means every row, also those older than any run; nothing is archived away today.
- Excel is the target; CSV is for other tools and stays UTF-8 with comma separators like the other CSV
  downloads.
- No full-text search in the details for now; the downloaded XLSX covers that.
