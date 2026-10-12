# Implementation Plan: Kontrollgrunnlag før godkjenning

**Branch**: `002-pre-approval-control` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-pre-approval-control/spec.md`

## Summary

This feature makes forsystem's built-in guarantees visible, so Økonomi can run BF1 T01, T06, T10
and T15 in the app. It has four parts:

- **Run summary.** A run summary on the run page, plus an email block for the covering email to
  regnskap.
- **LG04 reconciliation.** At export, the LG04 bytes are read back in memory by an independent
  reader and checked before anything is archived. A mismatch stops the export, leaves the run
  GODKJENT, logs the event and explains what happened on the run page. A match is stored as an
  `lg04_avstemming` row.
- **Usage reconciliation.** A view per run that compares imported usage with invoiced usage. Each
  skipped usage row is linked to the finding that blocked it.
- **Spreadsheet before approval.** The CSV/XLSX download is available before approval, with numeric
  cells and the customer name frozen at generation.

Planning found a confirmed defect (research R1): re-importing a period's usage after any run was
generated, even a discarded one, fails on a foreign key. This breaks the T08 correction flow.
Fixing it (D1: keep superseded usage rows, and record the run's import) is a prerequisite for the
reconciliation view. It ships first, as its own `fix:` commit.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x (web, data-jdbc, security), Flyway, Thymeleaf + htmx,
Apache POI (existing, for XLSX), openhtmltopdf (existing, for PDF). No new dependencies.

**Storage**: PostgreSQL 16 (migrations V8–V11, one per commit). Export artifacts go through the existing
`FileArchive` port.

**Testing**: JUnit 5 and AssertJ. Testcontainers PostgreSQL ITs. The existing LG04 golden tests
stay untouched.

**Target Platform**: Linux container on AKS (dis-core). Local `docker compose`.

**Project Type**: Server-rendered web application (single Maven module, modulith packages)

**Performance Goals**: The run page stays fast for a production-size run (about 2k orders, LG04
around 25 MB): it reads a stored reconciliation row and never re-reads the file per view. Export
adds one in-memory parse of the LG04, which is linear and around 1 s for 30k orders. The
reconciliation view and spreadsheet are in-memory over ≤ 30k rows (the LoadIT scale).

**Constraints**:
- LG04 output byte-identical
- billing period only from the run
- no VAT math (OQ-11)
- no new roles
- nothing written to the archive before the LG04 is proven correct

**Scale/Scope**: About 60–300 tjenesteeiere × 6 products per month. LoadIT ceiling is 5000 × 6.

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | No new accounting values. VAT shown only as "uavklart" (OQ-11). Order mode taken from the export's own assembly (OQ-1), not assumed |
| II. LG04 contractual | ✅ | `Lg04Skriver` unchanged and golden tests untouched. The new `Lg04Leser` only *reads*, and is itself pinned by the golden file |
| III. Traceability | ✅ **strengthened** | D1 stops deleting usage history. Runs record their import. Skipped rows are linked to their finding. Every export stores a reconciliation with its SHA-256 |
| IV. Explicit period | ✅ | All figures, file names and views take the period from the run |
| V. Portability | ✅ | Only the `FileArchive` port is used (`lagre`/`hent`). No new adapters |
| VI. Secrets | ✅ | None involved |
| VII. Humans approve money | ✅ | Nothing auto-approves or auto-exports. A failed check *prevents* the export; the human export action remains |

Post-design re-check (after data-model and contracts): unchanged, all ✅. No complexity-tracking
entries.

## Project Structure

### Documentation (this feature)

```text
specs/002-pre-approval-control/
├── spec.md
├── plan.md              # this file
├── research.md          # findings R1–R8, decisions D1–D9
├── data-model.md        # V8–V11 sketch, entities, invariants, spreadsheet columns
├── quickstart.md        # automated + manual validation
├── contracts/ui.md      # endpoints, page sections, audit events, file names
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code

```text
src/main/resources/db/migration/
├── V8__bruksdata_per_import.sql             # D1
├── V9__lg04_avstemming.sql                  # D6
├── V10__kontrollfunn_bruksdata.sql          # D2
└── V11__faktura_kundenavn.sql               # D9

src/main/java/no/digdir/forsystem/
├── usage/
│   ├── UsageImportService.java              # D1: no delete on re-import; bruksdataFor() = VALIDERT import's rows;
│   │                                        #     new gjeldendeImport(Periode) for billing
│   └── BruksdataRepository.java             # findByImportId already exists; drop deleteByPeriode
├── billing/
│   ├── FakturakjoringService.java           # D1 record import; D2 collect skipped-row ids per finding key;
│   │                                        #     D9 snapshot kundenavn; sammendrag(id)
│   ├── Fakturakjoring.java, Faktura.java    # + bruksdataImportId, + kundenavn
│   ├── KontrollfunnBruksdata(.java|Repository)   # D2 link table
│   ├── Kjoringssammendrag.java              # computed figures (record)
│   ├── BruksdataavstemmingService.java      # D2 view model: per product×type + skipped rows + invariant
│   └── Lg04Avstemming(.java|Repository)     # D6 stored reconciliation
├── export/
│   ├── lg04/Lg04Leser.java                  # D4 independent reader → Lg04Innhold(antallOrdrer, sumOre, feil)
│   ├── Lg04Avvik.java                       # D7 Regelbrudd subtype carrying both sides' figures
│   ├── EksportService.java                  # D3 build→read→compare→archive→verify sha→store→EKSPORTERT;
│   │                                        #     forventetAntallOrdrer(); loggStoppetEksport(); avstemmingFor()
│   ├── FakturagrunnlagRegneark.java         # D8 single column definition for export + download
│   ├── RegnearkEksport.java                 # D8 typed cells (BigDecimal → numeric)
│   └── (PDF html in EksportService)         # D9 customer name in the PDF heading
├── common/Handling.java                     # + EKSPORT_STOPPET, LASTET_NED
└── web/FakturakjoringController.java        # summary/email/reconciliation on detalj; catch Lg04Avvik → flash;
                                             #     /avstemming, /lg04-kontroll, /fakturagrunnlag.{csv,xlsx}

src/main/resources/templates/kjoring/
├── detalj.html                              # sections per contracts/ui.md
└── avstemming.html                          # new

src/test/java/no/digdir/forsystem/
├── usage/ReimportEtterForkastIT.java        # R1 regression (written first, fails before D1)
├── export/lg04/Lg04LeserTest.java
├── billing/EksportAvstemmingIT.java
├── billing/BruksdataavstemmingIT.java
├── billing/FakturagrunnlagRegnearkIT.java
└── billing/WebKjoringRoleIT.java            # extended
```

**Structure Decision**: The existing modulith packages stay as they are. The dependency direction
is unchanged: web → export → billing → usage/registry → common. The reconciliation *model* lives in
billing, because it owns runs, lines and findings. The LG04 *reader* lives in `export.lg04`, next to
the writer it checks.

## Delivery order (one commit each, `mvn verify` green after each)

1. `fix(usage): keep superseded bruksdata so a discarded run's lines stay valid`. Covers D1, the V8
   part for bruksdata/fakturakjoring, and `ReimportEtterForkastIT`. Independently valuable: it
   unblocks BF1 T08 even if the rest slips.
2. `feat(export): reconcile LG04 against the run before archiving`. Covers D3–D7, the run summary
   and email block (US1).
3. `feat(billing): usage reconciliation view per run`. Covers D2 and US2.
4. `feat(export): fakturagrunnlag spreadsheet before approval, with frozen customer name`. Covers
   D8, D9 and US3.
5. `docs`:
   - funksjonsoversikt (new F-IDs)
   - the local BF1 checklist, which is not in the repo (mark C-01/C-06/C-14 done; T08 unblocked)
   - brukerveiledning (the new sections)
   - docs/03 (V8–V11)
   - runbook (stopped-export procedure)

Migrations: one per commit, so every commit migrates cleanly on its own:

| Migration | Commit | Contents |
|---|---|---|
| V8 | 1 | bruksdata constraint swap and `fakturakjoring.bruksdata_import_id` |
| V9 | 2 | `lg04_avstemming` |
| V10 | 3 | `kontrollfunn_bruksdata` |
| V11 | 4 | `faktura.kundenavn` |

data-model.md shows them together as one sketch.

## Complexity Tracking

No violations. Nothing to justify.
