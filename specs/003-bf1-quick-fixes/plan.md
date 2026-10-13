# Implementation Plan: Small fixes from Økonomi's BF1 findings

**Branch**: `003-bf1-quick-fixes` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-bf1-quick-fixes/spec.md`

## Summary

Five small, independent changes that answer Økonomi's BF1 findings and close two control gaps:

- **F06**: the price-version page lists only the products that need a price.
- **F05**: the price-version pages explain gyldig fra/til in words, using the same rule generation
  uses.
- **F03, F01**: the product list shows active products first, plus dimension columns with
  configurable names.
- **C-09**: a new ADVARSEL for an active customer with no usage.
- **C-08**: export-file downloads are logged and checked against their run.

No migration and no new endpoint. See [research.md](./research.md) for R1–R5 and D1–D6.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Thymeleaf + htmx. No new dependencies.

**Storage**: PostgreSQL 16. No schema change (data-model.md).

**Testing**: JUnit 5, Testcontainers ITs, existing fixtures

**Target Platform**: AKS (dis-core); local `docker compose`

**Project Type**: Server-rendered web application (single Maven module)

**Performance Goals**: The C-09 check is a set difference over data already loaded in `bygg()`, so it
adds no queries.

**Constraints**:
- no dimension names hardcoded (OQ-2)
- the help text must state the exact rule `aktivVersjonFor` applies
- LG04 unchanged

**Scale/Scope**: About 300 customers and 6 products.

## Constitution Check

| Principle | Status | How |
|---|---|---|
| I. Never invent business values | ✅ | Dimension names are configuration, empty by default. The no-usage severity is a documented assumption, not a business value |
| II. LG04 contractual | ✅ | Not touched |
| III. Traceability | ✅ | Downloads join the audit trail. The new finding is stored like the others |
| IV. Explicit period | ✅ | Coverage is computed from the version's dates, never from today |
| V. Portability | ✅ | Only `FileArchive.hent`, as today |
| VI. Secrets | ✅ | None |
| VII. Humans approve money | ✅ | The new control only warns. The approval flow is unchanged |

Post-design re-check: all ✅. No complexity tracking needed.

## Project Structure

```text
specs/003-bf1-quick-fixes/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, contracts/ui.md
└── tasks.md

src/main/java/no/digdir/forsystem/
├── registry/PrisversjonService.java      # produkterFor(id), dekning(versjon)  (D1, D2)
├── registry/Prisdekning.java             # covered-periods value: første, siste (nullable), ingen()
├── registry/ProduktService.java          # oversikt(): active first  (D3)
├── web/Dimensjonsnavn.java               # @ConfigurationProperties forsystem.produkt.dimensjoner  (D4)
├── web/PrisversjonController.java        # uses produkterFor + dekning
├── web/ProduktController.java            # uses oversikt + Dimensjonsnavn
├── billing/Kontrollkode.java             # + AKTIV_KUNDE_UTEN_BRUK  (D5)
├── billing/FakturakjoringService.java    # raise it in bygg()
├── export/EksportService.java            # lastNedEksportfil(kjoringId, filId)  (D6)
└── web/FakturakjoringController.java     # download via the service
src/main/resources/
├── application.yaml                      # forsystem.produkt.dimensjoner.* ← PRODUKT_DIMn_NAVN
└── templates/{prisversjon/liste,prisversjon/detalj,produkt/liste,produkt/rediger}.html
```

**Structure Decision**: The existing packages stay. `Dimensjonsnavn` is display configuration, so it
lives in `web`. Coverage is a registry concern because it is the price version's own rule, and
billing's `aktivVersjonFor` keeps its query.

## Delivery order (one commit each, `mvn verify` green after each)

1. `feat(registry): price versions list only products that need a price and explain their dates` (F06, F05)
2. `feat(registry): product list shows active first and dimension columns` (F03, F01)
3. `feat(billing): warn when an active customer has no usage in the period` (C-09)
4. `feat(export): log export-file downloads and check they belong to the run` (C-08)
5. `docs`: brukerveiledning (§5 Prisversjoner, §4 Produkter, §11 Kontrollfunn), funksjonsoversikt,
   runbook (dimension env vars)

## Complexity Tracking

None.
