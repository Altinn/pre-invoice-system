# UI contract: Versioned correction runs

All write actions retain CSRF protection and current role enforcement. Read pages require LESER;
candidate, comparison disposition and correction generation require FORVALTER; approval/export keep
GODKJENNER.

## Bruksdata routes

| Method and route | Behavior |
|---|---|
| `POST /bruksdata/hent-dwh` | Existing behavior before export. After an exported version exists, validate/archive a correction candidate and redirect to its comparison. |
| `POST /bruksdata/last-opp` | A recognized complete DWH export follows the same conditional flow. Native forsystem CSV keeps ordinary behavior and is refused as a correction candidate. |
| `GET /bruksdata/sammenligninger/{id}` | Paged/filterable diff; query: `q`, `produkt`, `type`, `endringstype`, `fortegn`, `side`. Shows baseline version/checksum and candidate checksum/time. |
| `POST /bruksdata/sammenligninger/{id}/kjoring` | Form fields: repeated `endringId`, `aarsak`, `grunnlagKjoringId`. Atomically creates next correction version or rejects stale/invalid selection. |
| `POST /bruksdata/sammenligninger/{id}/avvis` | Repeated `endringId` + required `aarsak`; records explicit dismissal. |
| `POST /bruksdata/sammenligninger/{id}/sammenlign-igjen` | Reuses retained candidate against latest exported version; idempotent. |

### Comparison page

Header:

- `Endringer i bruksdata · januar 2027`
- `Sammenligner fakturert grunnlag V1 med kandidat hentet <time>`
- old/new raw checksum links and status tag

Summary cards:

- changed / unchanged counts
- additions / changes / removals
- signed quantity deltas per product
- signed cost delta and estimated invoice delta
- reconciliation status

Table columns:

1. checkbox (unchecked initially)
2. change type
3. organisation number (and customer name where known)
4. product
5. usage type
6. old
7. new
8. difference, with explicit `+`/`−`
9. disposition/status

Actions:

- `Velg alle på siden`; a separate explicit action may select all filtered results and states count
- `Opprett korrigering V<n>` requires selection and reason
- `Avvis valgte endringer` requires a different confirmation and reason
- no action is available when comparison is superseded/stale or has zero changes

Accessibility:

- checkboxes have labels containing organisation, product and signed difference
- sign is expressed in text as well as color
- table headers and selection error summary are screen-reader navigable
- focus returns to the error summary on invalid submit

## Kjøring routes and pages

Existing routes remain. Shared header becomes:

- ordinary: `Kjøring januar 2027 · V1`
- correction: `Korrigering januar 2027 · V2`

The run list adds **Type/versjon** and **Endringer**. Same-month rows are ordered newest version first.

Correction Oversikt adds:

- previous-version link
- correction reason
- selected change count, positive/negative/net totals
- candidate/comparison link
- export gate warning when OQ-6 mechanism is not enabled

Correction Kontroller adds a signed correction reconciliation. Fakturaer and Filer reuse existing
tabs. Historikk includes candidate/comparison/selection events linked to their entities.

## File contract

Every correction download contains version in its filename:

- `fakturagrunnlag-2027-01-v2-GENERERT.xlsx`
- `lg04-2027-01-v2.txt`
- `pdf-2027-01-v2.zip`
- `fakturagrunnlag-2027-01-v2.csv`

Ordinary V1 archived file contents remain unchanged; whether their user-visible names gain `v1` is a
UI migration choice. New V1 downloads SHOULD include `v1`, while already archived names are retained.

Correction spreadsheets add columns after existing provenance columns: version, change type, old
value, new value, signed difference, comparison ID. Numeric cells stay numeric and signed.

## Error messages

- No selection: `Velg minst én endring som skal være med i korrigeringen.`
- Missing reason: `Skriv hvorfor korrigeringen opprettes.`
- Stale baseline: `Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt.`
- In-flight run: `Det finnes allerede en pågående kjøring for januar 2027.`
- Native CSV correction: `Korrigering krever en komplett eksport fra datavarehuset.`
- Export gate: `Korrigeringen kan ikke eksporteres før avregningsmåten er avklart og testet med regnskap (OQ-6).`

