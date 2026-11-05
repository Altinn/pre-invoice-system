# Implementation Plan: Acknowledge control findings and export the Kontroller lists

**Branch**: `claude/013-kvittering-funn-fac4c8` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

## Summary

`V15__kontrollfunn_aksept.sql` adds `akseptert_av`/`akseptert_at` to `kontrollfunn`, with checks that
both are set together and only on ADVARSEL rows. `FakturakjoringService` gets `settAksept` (one finding)
and `settAkseptForKode` (all warnings of one code), both locking the run row like `godkjenn`, refusing
anything but a GENERERT run and ADVARSEL findings of that run, and logging `AKSEPTERTE_FUNN` /
`ANGRET_AKSEPT` on the run. `godkjenn` also refuses while an ADVARSEL is unaccepted. The web layer counts
open warnings for the badge, the run list and the Oversikt checklist; the Kontroller tab gets a «Funn
akseptert» column with auto-submitting checkboxes (forsystem.js, with a no-JS submit button). The
security rule lets FORVALTER and GODKJENNER post to `/kjoringer/*/kontrollfunn/**`. The existing list
endpoint gains `kontrollfunn`, `avstemming-bruksdata` and `ikke-fakturert`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JDBC, Spring MVC, Spring Security, Thymeleaf +
htmx, Designsystemet CSS, Apache POI (existing `RegnearkEksport`)

**Storage**: PostgreSQL 16 / Flyway — V15 adds two nullable columns and two checks to `kontrollfunn`

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers; LG04 golden file unchanged

**Target Platform**: Linux container (dis-core tt02/prod); local Docker Compose

**Project Type**: Server-rendered web application

**Performance Goals**: A group acceptance of a few hundred findings is one transaction

**Constraints**: humans approve (principle VII); billing period explicit; no invented values

**Scale/Scope**: Tens to a few hundred findings per run

## Constitution Check

*Gate before Phase 0: PASS. Re-checked after design: PASS.*

| Principle | Evaluation |
|---|---|
| I. Never Invent Business Values | PASS. No accounting values. The rules (warnings only, roles, per finding) are design choices recorded in the spec and docs/09, not business values. |
| II. LG04 Output Is Contractual | PASS. Export untouched; the golden test runs unchanged. |
| III. Traceability Is Structural | PASS. Who and when is on the finding row and in the run's history; history rows are appended, never edited. |
| IV. Explicit Billing Period | PASS. Nothing date-dependent; timestamps are audit stamps only. |
| V. Portability Behind Ports | PASS. No new dependency. |
| VI. Secrets From Configuration Only | PASS. Nothing secret. |
| VII. Humans Approve Money | PASS, strengthened. Approval now also requires a named person to have accepted every warning; approval itself stays GODKJENNER-only. |

## Project Structure

```text
specs/013-kvittering-kontrollfunn/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
├── contracts/ui.md
└── checklists/requirements.md

src/main/resources/db/migration/V15__kontrollfunn_aksept.sql      # NEW
src/main/java/no/digdir/forsystem/
├── billing/Kontrollfunn.java                 # + akseptertAv, akseptertAt, erAkseptert(), medAksept()
├── billing/KontrollfunnRepository.java       # + exists/count open warnings, findByKjoringIdAndKode
├── billing/FakturakjoringService.java        # settAksept, settAkseptForKode, godkjenn gate, antallAapneAdvarsler
├── common/Handling.java                      # + AKSEPTERTE_FUNN, ANGRET_AKSEPT
├── export/EksportService.java                # lists kontrollfunn, avstemming-bruksdata, ikke-fakturert
├── web/Funngruppe.java                       # holds the findings; counts accepted
├── web/Funnstatus.java                       # NEW: blokkerende / åpne / aksepterte counts + wording
├── web/FakturakjoringController.java         # aksept endpoints, counts, download path
├── web/Kjoringsrad.java                      # + antallAksepterte
└── web/security/SecurityConfig.java          # POST /kjoringer/*/kontrollfunn/** for FORVALTER+GODKJENNER
src/main/resources/templates/kjoring/kontroller.html, detalj.html, liste.html, fragmenter.html
src/main/resources/static/js/forsystem.js     # [data-auto-send] checkbox submits its form
src/test/java/no/digdir/forsystem/billing/KontrollfunnAkseptIT.java    # NEW
src/test/java/no/digdir/forsystem/billing/KontrollerEksportIT.java     # NEW
src/test/java/no/digdir/forsystem/billing/BillingFixture.java          # + aksepterAlleAdvarsler helper
docs/03-datamodel.md, docs/brukerveiledning.md, docs/funksjonsoversikt.md, docs/09-beslutningslogg.md
```

**Structure Decision**: Acceptance lives on the finding row (the thing accepted) and in the billing
service next to `godkjenn`, which it gates. Exports reuse `EksportService.liste`.

## Complexity Tracking

No constitution violations to justify.
