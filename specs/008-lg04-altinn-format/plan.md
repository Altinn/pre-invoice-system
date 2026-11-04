# Implementation Plan: LG04 export in FEL's Altinn format

**Branch**: `feat/lg04-altinn-format` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-lg04-altinn-format/spec.md`

## Summary

Switch the LG04 export from the DPI/eSignering layout (one order per invoice line) to FEL's Altinn
format (one order per invoice, a header line plus a line pair per product), locked by FEL's `test_3`
sample as the golden file. Most of this is built in commit `c344466` (US1, FR-001..008). The
remaining work is small and has no schema change:

- generation records a new ADVARSEL finding when one invoice carries several reference pairs, which
  the export already splits into one order each (FR-009);
- VALUE_1 is blank when a line has no quantity, instead of FEL's `-100` error marker (FR-010);
- the run summary gains a total incl. 25 % mva computed per invoice, and the "mva uavklart" labels
  go (FR-011);
- header and footer text are cut at their 120-character field width (FR-012);
- docs record FEL's answers (FR-013).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Thymeleaf + htmx

**Storage**: PostgreSQL 16 with Flyway — no migration in this feature (`kontrollfunn.kode` is free
text); archived LG04 files via the existing `FileArchive` port

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL; LG04 golden-file test
(`Lg04SkriverTest`) and the independent reader (`Lg04LeserTest`)

**Target Platform**: Linux container (dis-core tt02/prod); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: Unchanged — export of a few hundred invoices in seconds

**Constraints**: LG04 byte-exact against the golden file; Windows-1252 written directly; no
invented business values; billing period explicit; buntnummer from the export time (not the period)

**Scale/Scope**: A few hundred invoices and up to ~10 products per invoice per month

## Constitution Check

*Gate before Phase 0: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. Every field value comes from FEL's sample, the LG04_55 spec or FEL's written answers (25 % mva, blank VALUE_1 = 1.0). Kontering stays registry data (OQ-2); påslag, text line and footer content are out of scope until answered. |
| II. LG04 Output Is Contractual | PASS. The golden file changes deliberately to FEL's sample, in this feature's commits only, and is compared as a whole file. The VALUE_1 change does not touch the sample (both its lines have quantities). |
| III. Traceability Is Structural | PASS. No history is rewritten; archived files of earlier runs stay as exported. The split finding is stored with its invoice. |
| IV. Explicit Billing Period | PASS. Header text uses the run's period. Buntnummer is the export date by FEL's definition — a file attribute, not the billing period. |
| V. Portability Behind Ports | PASS. No new external dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret involved. |
| VII. Humans Approve Money | PASS. The split is a warning shown before approval; a human still approves and exports. |

*Post-design re-check: PASS.* The design adds one enum value, one summary figure and two writer
rules; no new module, table or dependency.

## Project Structure

### Documentation (this feature)

```text
specs/008-lg04-altinn-format/
├── spec.md
├── plan.md              # this file
├── research.md          # decisions R1–R6
├── data-model.md        # LG04 order/line, finding, summary figures
├── quickstart.md        # how to verify
├── contracts/
│   └── lg04-altinn.md   # the file layout we write, field by field
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/java/no/digdir/forsystem/
├── billing/
│   ├── Kontrollkode.java              # + DELT_REFERANSE (ADVARSEL)
│   ├── FakturakjoringService.java     # finding at generation; sumInklMva in sammendrag()
│   └── Kjoringssammendrag.java        # + sumInklMva
├── export/
│   ├── EksportService.java            # order assembly (built in c344466)
│   ├── Lg04AvstemmingVisning.java     # buntnummer (built)
│   └── lg04/
│       ├── Lg04Skriver.java           # blank VALUE_1 without quantity; 120-char text fields
│       ├── Lg04Leser.java             # header + pairs (built)
│       └── Lg04Ordre.java             # order + product lines (built)
src/main/resources/templates/kjoring/detalj.html   # e-mail block and summary: ex./incl. mva

src/test/java/no/digdir/forsystem/
├── export/lg04/Lg04SkriverTest.java   # golden file; VALUE_1 blank/0; 120-char cap
├── billing/EksportIT.java             # split invoice → finding + two orders
└── billing/EksportAvstemmingIT.java   # e-mail block shows ex./incl. mva
src/test/resources/lg04/altinn-test3.txt           # golden file (built)

docs/07-open-questions.md, docs/09-beslutningslogg.md, docs/brukerveiledning.md,
docs/funksjonsoversikt.md
```

**Structure Decision**: Existing modules only. The finding belongs in generation (billing), next to
`NULLBELOP`, because it describes the invoice and must be visible before approval; the export keeps
the split itself, since only the export knows the LG04 layout.

## Complexity Tracking

No constitution violations to justify.
