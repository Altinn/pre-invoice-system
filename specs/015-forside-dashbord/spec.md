# Feature Specification: Start page as a dashboard

**Feature Branch**: `feat/015-forside-dashbord`

**Created**: 2026-11-11

**Status**: Implemented

**Input**: The start page is five link cards that repeat the top menu. Replace it with a dashboard that
answers «where are we in this month's invoicing, and what do I do next?», plus a way into a future
learning centre.

## Context

`/` renders five `ds-card` links (Produkter, Prisversjoner, Bruksdata, Kjøringer, Hendelseslogg) — the
same five entries as the header menu. Everything a dashboard needs is already stored: usage imports
(`bruksdata_import`, with `hentet_at` for DWH fetches), runs (`fakturakjoring`) with invoices, lines,
findings and threshold exclusions, open usage comparisons, products with their kontering, price
versions, and the audit trail. No schema change is needed.

## Clarifications

### Session 2026-11-11 (design decisions settled in this spec)

- Q: Which billing period does the dashboard show? → A: **The latest period with data**, never the
  clock (CLAUDE.md: the period is never derived from the current date). Rule, in order:
  1. If an ordinary run (`type = ORDINAER`) is unfinished (GENERERT or GODKJENT), its period — the
     oldest such period if there are several. An unfinished month is not hidden by the next month's usage.
  2. Otherwise the newest `periode` among non-FORKASTET ordinary runs and non-AVVIST, non-KANDIDAT
     usage imports.
  3. No such data → no period; the page shows the welcome, the learning card and a first step.
  Correction runs (KORRIGERING) never choose the period; an unfinished one is listed under «Trenger
  oppmerksomhet».
- Q: May the clock be used at all? → A: Only for things that are not a billing period: the greeting
  («God morgen/dag/kveld»), today's date, relative times («for 3 dager siden») and the next scheduled
  DWH fetch (from `forsystem.dwh.planlegging.cron`, shown only when scheduling is enabled).
- Q: Who sees amounts? → A: Everyone who can log in (LESER and up). It is an internal tool and every
  amount is already visible on the run pages to LESER.
- Q: Which name does the greeting use? → A: The OIDC `given_name` claim, else the first word of
  `name`, else the part of an e-mail-shaped username before the first `.`/`@`, capitalised. With none of
  these (the local stub user `dev`), the greeting has no name.
- Q: The learning centre? → A: Out of scope. The card links to `/laering`, a placeholder page
  («kommer snart»). A later spec replaces it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See where this month stands and go to the next step (Priority: P1)

As a FORVALTER or GODKJENNER I open forsystem and see, for the dashboard period, a sentence that says
where invoicing stands, a progress strip (Bruksdata hentet → Generert → Kontrollert og godkjent →
Eksportert) and one button to the next step.

**Acceptance Scenarios**:

1. **Given** a VALIDERT import for 2027-01 and no run, **When** I open `/`, **Then** the period is
   «januar 2027», «Bruksdata hentet» is done, «Generert» is current, and a FORVALTER gets «Generer kjøring».
2. **Given** a GENERERT run with 2 unaccepted warnings and no blocking findings, **Then** the sentence
   says it awaits approval and that 2 warnings must be assessed, and the button goes to the run's controls.
3. **Given** an unfinished January run and a VALIDERT February import, **Then** the period stays January
   and «Trenger oppmerksomhet» says usage for februar 2027 is fetched.
4. **Given** an unfinished correction run for 2026-12 and an exported January run, **Then** the period is
   January and the correction is listed under «Trenger oppmerksomhet».

### User Story 2 - Key numbers and per-product split (Priority: P1)

Tiles show for the dashboard period: fakturagrunnlag (sum of invoices) with the change against the
previous period's exported run, number of invoices and how many fell under the threshold, transactions
(BRUKSVOLUM), and the last DWH fetch. A bar list splits the amount per product (or, before generation,
the usage volume per product).

### User Story 3 - Attention list, recent activity, learning card (Priority: P2)

«Trenger oppmerksomhet» lists, each with a link: blocking findings and unaccepted warnings in unfinished
runs, unfinished correction runs, open usage comparisons, newer usage than the dashboard period, active
products missing konto or artikkel-id, and a missing active price version for the dashboard period or
the next one. Empty list → «Alt i rute». «Siste aktivitet» shows the six newest audit rows (downloads
excluded) as short sentences. A card «Ny i forsystem?» links to `/laering`.

### Edge Cases

- Empty database (dry-run, first deploy): no period, no tiles; a welcome and «Hent bruksdata for å komme
  i gang» linking to `/bruksdata`.
- No previous exported period: the change line is left out (no «+∞ %»).
- An import staged by DWH but not confirmed (MOTTATT): «Bruksdata hentet» is current, «venter på bekreftelse».
- DWH never fetched: tile reads «Aldri» with «Ingen henting fra datavarehuset ennå».

## Requirements *(mandatory)*

- **FR-001**: `/` no longer repeats the menu as cards.
- **FR-002**: The dashboard period follows the rule in Clarifications and is never derived from the clock.
- **FR-003**: Panels: welcome (greeting, date, status sentence, one primary action), progress strip,
  key-number tiles, per-product bars, «Trenger oppmerksomhet», learning card, «Siste aktivitet».
- **FR-004**: Only Designsystemet classes and `--ds-*` tokens; no inline styles; light and dark mode;
  numbers through `Tallformat`.
- **FR-005**: `/laering` exists as a placeholder for every logged-in role.
- **FR-006**: Read-only: opening `/` writes nothing, including to the audit trail.

## Success Criteria *(mandatory)*

- An empty database and a seeded one both render `/` with HTTP 200 (integration tests).
- The period rule's four acceptance scenarios hold (integration tests).
- `mvn verify` green.
