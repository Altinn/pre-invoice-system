# Implementation Plan: Invoice texts per invoicing run

**Branch**: `feat/009-kjoring-fakturatekster` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-kjoring-fakturatekster/spec.md`

## Summary

Store the three LG04 invoice texts — header (`long_info1`), footer (`long_info2`) and Vår
referanse (`responsible`/`responsible2`) — on the run itself. Generation fills Økonomi's defaults;
a FORVALTER can change them on the Oversikt tab while the run is GENERERT; approval freezes them;
the export writes them instead of the hard-coded header, the per-invoice link and the configured
responsible persons. Validation enforces the LG04 widths (120/120/25), single-line text and
Windows-1252. Regnskap's printed-invoice limits (90/65) are advisory warnings, `TODO(OQ-23)`.
One Flyway migration (V13) adds the columns; `EksportKonfig` goes away.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx,
Designsystemet CSS

**Storage**: PostgreSQL 16 with Flyway — `V13__kjoring_fakturatekster.sql` adds three nullable
text columns to `fakturakjoring` with length checks, a "required while GENERERT/GODKJENT" check,
and backfills runs awaiting approval/export

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL; LG04 golden-file test
(`Lg04SkriverTest.fileIsByteIdenticalToFelsSample`) must stay byte-identical

**Target Platform**: Linux container (dis-core tt02/prod); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: Unchanged

**Constraints**: LG04 byte-exact; Windows-1252 single-byte; no invented business values (defaults
are Økonomi's, 2026-11-04); approved runs immutable; every change audited

**Scale/Scope**: One form with three fields; one run per month

## Constitution Check

*Gate before Phase 0: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. The three defaults are Økonomi's written answer (docs/07 OQ-23). The printed-invoice limit is not confirmed, so it is a warning marked `TODO(OQ-23)`, never enforced. The configured placeholder responsible person (OQ-2) is removed rather than replaced by a guess. |
| II. LG04 Output Is Contractual | PASS. Field offsets do not move. The golden test keeps its own values; the writer additionally pads/cuts `responsible` to 25, which changes no byte of FEL's sample (21 characters + spaces). |
| III. Traceability Is Structural | PASS. Texts are stored with the run, so the file can be rebuilt from the database; every change is an `ENDRET` row with old and new value. Exported runs are not backfilled with texts they were not written with. |
| IV. Explicit Billing Period | PASS. The default header uses the run's own period (`periodeLabel()`), never the clock. |
| V. Portability Behind Ports | PASS. No new dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret involved. |
| VII. Humans Approve Money | PASS. Texts are part of what the GODKJENNER approves; they are locked from approval on, and editing and approval serialise on the run row. |

*Post-design re-check: PASS.* One value object, one service method, one endpoint, one migration.

## Project Structure

### Documentation (this feature)

```text
specs/009-kjoring-fakturatekster/
├── spec.md
├── plan.md              # this file
├── research.md          # decisions R1–R7
├── data-model.md        # columns, value object, audit entry
├── quickstart.md        # how to verify
├── contracts/
│   └── ui.md            # the Oversikt section and POST /kjoringer/{id}/tekster
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/resources/db/migration/V13__kjoring_fakturatekster.sql   # columns, checks, backfill

src/main/java/no/digdir/forsystem/
├── billing/
│   ├── Fakturatekster.java            # NEW: value object — defaults, limits, validation
│   ├── Fakturakjoring.java            # + topptekst, bunntekst, varReferanse; tekster()
│   ├── FakturakjoringRepository.java  # + row lock for approve/edit
│   └── FakturakjoringService.java     # defaults at generation; endreTekster(); godkjenn locks
├── export/
│   ├── EksportService.java            # writes the run's texts; refuses a run without them
│   ├── EksportKonfig.java             # DELETED (responsible + link base no longer used)
│   └── lg04/
│       ├── Lg04Ordre.java             # tekst/url → topptekst/bunntekst
│       └── Lg04Skriver.java           # responsible fields padded/cut to 25
└── web/FakturakjoringController.java  # POST /kjoringer/{id}/tekster
src/main/resources/templates/kjoring/detalj.html   # "Tekster på fakturaen" section

src/test/java/no/digdir/forsystem/
├── billing/FakturateksterTest.java    # NEW: validation unit tests
├── billing/KjoringFakturateksterIT.java  # NEW: defaults, edit, audit, lock, export, web
└── export/lg04/Lg04SkriverTest.java   # golden unchanged; responsible cut at 25
src/test/resources/application-test.yaml           # drop forsystem.eksport.*

docs/07-open-questions.md, docs/brukerveiledning.md, docs/funksjonsoversikt.md,
docs/09-beslutningslogg.md, docs/03-datamodel.md
```

**Structure Decision**: Existing modules only. The texts belong to the run (billing) because they
are approved with it; the export only reads them. Validation lives in a billing value object so the
service, not the web layer, guards the LG04 widths.

## Complexity Tracking

No constitution violations to justify.
