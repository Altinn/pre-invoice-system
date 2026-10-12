# UI contract: Kontrollgrunnlag før godkjenning

This is a server-rendered UI (Thymeleaf + htmx). No REST endpoints are added under `/api`. Role
rules come from `SecurityConfig` as they are today: GET is LESER, POST `/kjoringer/*/eksporter` is
GODKJENNER, and other POSTs are FORVALTER. **No security rule changes.**

## Endpoints

| Method | Path | Role | Purpose | Spec |
|---|---|---|---|---|
| GET | `/kjoringer/{id}` | LESER | Run page, extended with summary, email block, LG04 reconciliation and a link to the reconciliation view | FR-001..008 |
| POST | `/kjoringer/{id}/eksporter` | GODKJENNER | Unchanged URL. On `Lg04Avvik`: redirect to `/kjoringer/{id}` with a flash danger alert (FR-005a), with the run still GODKJENT | FR-004..005a |
| GET | `/kjoringer/{id}/lg04-kontroll` | LESER | Re-read and re-hash the archived LG04 and show the result. Read-only, writes nothing | FR-007 |
| GET | `/kjoringer/{id}/avstemming` | LESER | Usage reconciliation view | FR-009..013 |
| GET | `/kjoringer/{id}/fakturagrunnlag.xlsx` | LESER | Spreadsheet download (GENERERT/GODKJENT/EKSPORTERT). Logs `LASTET_NED` | FR-014..019 |
| GET | `/kjoringer/{id}/fakturagrunnlag.csv` | LESER | Same, as CSV | FR-014..019 |

On a FORKASTET run, the two spreadsheet URLs answer with a `Regelbrudd` page: "Forkastede kjøringer
kan ikke lastes ned som fakturagrunnlag". The UI does not show the buttons for such runs.

## Run page (`kjoring/detalj.html`) additions

Sections, top to bottom, after the status tag:

1. **Sammendrag** (always shown):
   - periode
   - antall fakturaer
   - antall LG04-ordrer, with the hint "én ordre per fakturalinje (OQ-1)" while that mode is active
   - antall linjer
   - sum beløp, labeled **"eksl./inkl. mva uavklart"**

   If Σ faktura ≠ Σ linjer: a danger alert that names both sums.
2. **Til e-post til regnskap**: a read-only text block with periode, antall fakturaer and samlet
   beløp (with the VAT label), plus a "Kopier" button using the Clipboard API. Shown for GODKJENT and
   EKSPORTERT runs.
3. **LG04-avstemming** (EKSPORTERT only):
   - Matched: a success alert, "LG04 stemmer med fakturagrunnlaget", followed by a two-column table
     (fil | kjøring) of orders and amount. Under the table: who checked it and when, and a link to
     `/lg04-kontroll`.
   - Run exported before this feature: the same layout, computed on the fly, captioned "avstemt mot arkivert fil nå (kjøringen
     ble eksportert før automatisk avstemming)".
   - File unreadable or checksum differs: a danger alert, "kunne ikke avstemmes", with the reason
     (FR-007).
4. **Stopped-export flash** (after a failed POST): a danger alert with the FR-005a text, e.g.

   > Eksport stoppet: LG04-filen har 8 ordrer / 12 300,00, fakturagrunnlaget har 9 ordrer /
   > 12 345,67. Ingen filer er laget, og kjøringen er fortsatt godkjent. Dette er en feil i
   > forsystemet — kontakt forsystem-teamet og eksporter på nytt når feilen er rettet.

5. **Links**: "Avstemming bruksdata", plus "Last ned fakturagrunnlag (XLSX, for Excel)" and "(CSV)"
   for non-FORKASTET runs.
6. The faktura table gets a **Kunde** column, using the D9 display rule.

## Reconciliation view (`kjoring/avstemming.html`)

- Header: the run, the periode, and the usage import it was built from (id, source CSV/DWH, filename,
  time), or "ukjent import (kjøring generert før avstemming fantes)".
- An info alert: "Viser bare bruk som kom inn i forsystemet. Bruk som datavarehuset filtrerer bort
  (f.eks. inaktive avtaler) er ikke med." (FR-013).
- The per product × type table, with the columns from data-model.md. Rows with a difference are
  highlighted.
- A success alert when everything was invoiced: "All bruksdata er med i fakturagrunnlaget"
  (FR-012).
- The "Ikke fakturert" table: orgnr, produkt, type, antall/beløp, kode and melding of the finding,
  or "årsak ikke registrert" for runs generated before this feature.
- A danger alert if the invariant fails (data-model.md).

## Audit events (`hendelseslogg`)

| Handling | Entitet | When | detaljer |
|---|---|---|---|
| `EKSPORTERTE` (existing) | FAKTURAKJORING | successful export | adds `antallOrdrer`, `sumOre`, `lg04Sha256` |
| `EKSPORT_STOPPET` (new) | FAKTURAKJORING | `Lg04Avvik` | `periode`, both sides' `antallOrdrer`/`sumOre`, structural errors |
| `LASTET_NED` (new) | FAKTURAKJORING | spreadsheet download | `periode`, `format` (CSV/XLSX), `status` |

## File names

- Download: `fakturagrunnlag-<ÅÅÅÅ-MM>-<STATUS>.<csv|xlsx>`, e.g. `fakturagrunnlag-2027-01-GENERERT.xlsx`.
- Archived at export: unchanged (`fakturagrunnlag-<ÅÅÅÅ-MM>.<csv|xlsx>`), with the same columns.
