# 04 — Build phases

Execute strictly in order. A phase is done when every acceptance criterion passes and
`mvn verify` is green. Calendar mapping targets the December 2026 dry-run and the
February 2027 first run.

## Phase 0 — Skeleton (target: early September 2026)

Scope: Maven project (`no.digdir:forsystem`, Java 21, Spring Boot 3.5.x), module packages
per docs/02, Flyway migrations V1–V6 from docs/03, docker-compose (app + PostgreSQL 16),
Dockerfile (multi-stage, temurin JRE slim), GitHub Actions (build + test + image),
Actuator health, structured JSON logging, OIDC security skeleton with a `local` dev
profile that stubs login (fixed dev user with all roles).

Acceptance:
- `docker compose up` → app on :8080, DB migrated, `/actuator/health` UP.
- `mvn verify` runs Testcontainers-based migration test (all migrations apply on a clean
  PostgreSQL 16 container).
- CI green on push; image published (registry configurable via repo variable).
- No Azure SDK anywhere yet.

## Phase 1 — Registries + UI (target: September 2026)

Scope: CRUD for produkt, prisversjon/pris, kunde, kunde_referanse, kundenummer_regel.
Thymeleaf + htmx + Designsystemet. Role checks (FORVALTER edits, LESER reads). Every write
lands in `hendelseslogg`. Price version lifecycle UTKAST → AKTIV → ARKIVERT with validation
(one AKTIV per date range; every active product must have a price in an AKTIV version).

Acceptance:
- The 2027 published price list can be entered and activated through the UI.
- A customer with: deviating fakturamottaker, product-specific fakturareferanse, and two
  kundenummer_regel rows (different servicekode) can be fully registered through the UI.
- Editing anything produces a hendelseslogg row visible on an audit page.
- Integration tests (Testcontainers) cover each repository; MVC tests cover role
  enforcement (LESER cannot POST).

## Phase 2 — Usage import (target: October 2026)

Scope: CSV upload (period, orgnr, product code, type, antall/belop), parsing + validation
(unknown product, malformed orgnr, wrong period vs declared, duplicates, negative values),
staging report shown *before* commit (row counts, sums per product, rejects with line
numbers), re-import semantics per docs/03 decision 4. `UsageDataSource` port so DWH can
slot in later. CSV column contract documented in `docs/csv-format.md` (agent writes it,
marks SMS/Azure columns as provisional — OQ-3).

Acceptance:
- Golden-path CSV imports; totals on screen match the file.
- Every validation rule has a failing-file test; a rejected file leaves zero rows.
- Re-import of the same period replaces rows and marks the old import AVVIST.
- Import of a period with an existing non-FORKASTET run is blocked with a clear message.

## Phase 3 — Generation engine + controls + approval (target: October–November 2026)

Scope: for a chosen period and AKTIV price version: resolve customers from usage orgnr,
group lines per (kunde, kundenummer) via kundenummer_regel, compute BRUKSVOLUM lines
(antall × enhetspris) and pass-through cost lines, snapshot references/recipient, persist
faktura + fakturalinje, run controls into kontrollfunn:
- BLOKKERENDE: usage orgnr with no kunde; kunde without kundenummer_regel; INAKTIV
  avtalestatus; product without price in the active version; product with NULL kontering.
- ADVARSEL: sum deviation vs previous period beyond ±30 %; zero-amount lines; new customer.
Status flow GENERERT → GODKJENT (GODKJENNER role, blocked while BLOKKERENDE findings
exist) → EKSPORTERT; FORKAST at any pre-export point. Run overview + detail screens
(invoice table with findings, drill-down to lines and their bruksdata).

Acceptance:
- Deterministic engine: same inputs → identical lines (ordering included); covered by an
  engine test with a fixture dataset that exercises every FinMod rule from docs/01.
- Each control has a test proving it fires and (for BLOKKERENDE) blocks approval.
- Rounding: line belop = round-half-up to 2 decimals; totals = sum of lines exactly.
- Full state machine covered by tests, including the partial-unique re-run rule.

## Phase 4 — Exports (target: November 2026)

Scope: port the LG04 writer from `../agresso` (`Linje1/2/3`, `RenderLine`) with its golden
tests, then adapt: Windows-1252 output, period from run parameter, kontering from produkt,
multiple amount lines per invoice (behind a flag until OQ-1 is answered — default: split
into one order per line, the safe interpretation), responsible person from configuration.
PDF detail view per invoice (HTML template → PDF, e.g. openhtmltopdf), zip download per run.
CSV/XLSX export of the run's lines. All exports archived via `FileArchive` + `eksportfil`
row with sha256. Export allowed only from GODKJENT; sets EKSPORTERT.

Acceptance:
- Ported golden tests pass byte-identically for the single-line case.
- New golden file covers a multi-line invoice under the flag's default behavior.
- Encoding test proves æ/ø/å land as Windows-1252 bytes; line length always 4324.
- LG04 line count = 3 × order count; control sums match run totals (test).
- PDF zip contains one PDF per faktura, named `<kundenummer>-<faktura_uuid>.pdf`.

## Phase 5 — Hardening + dry-run readiness (target: early December 2026)

Scope: demo/seed dataset (realistic fake customers incl. every special rule, fake usage
CSV); an end-to-end scenario test (import → generate → approve → export → verify LG04
against a golden file); Azure deployment (Container Apps or AKS: managed identity, Blob
adapter active, Entra ID issuer, Key Vault-backed env); ops runbook `docs/runbook.md`
(monthly procedure, re-run procedure, what to do on DFØ error email); UX pass on the run
detail screen with the people who will approve.

Acceptance:
- A non-developer can execute the full monthly cycle on the deployed app using the runbook
  and the demo dataset, ending with a downloaded LG04 + PDF zip.
- `docker compose up` still yields the complete flow locally (no Azure dependency).
- Load sanity: 5 000 customers × 6 products generates in < 1 min.

## Explicitly NOT in MVP

DFØ delivery automation and receipt ingestion; CRM/D365 integration; DWH direct reads;
scheduler/cron; avregning; notifications; PDF hosting for tjenesteeiere; migration of the
old services (dp/e-signering/altinn/bod). See docs/06 for how each slots in later.
