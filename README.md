# Forsystem — fakturering av Altinn-produkter (FinMod)

Invoicing pre-system ("forsystem") for Digdir's Altinn products under the new FinMod price
model, effective 2027-01-01. The system assembles the invoice basis (fakturagrunnlag) from
usage data, prices and customer registries; a human verifies and approves; export files are
produced for Unit4 (LG04 via DFØ), plus PDF detail views and CSV/XLSX.

**Status**: MVP feature-complete (Phases 0–4); Phase 5 demo readiness done, Azure deployment
deferred. The full monthly cycle runs locally end-to-end — see the demo below and
[docs/runbook.md](docs/runbook.md).

## Build & run

Requires Java 21 and Docker. The stack runs fully offline; login is stubbed to a dev user
with all roles in the `local` profile.

```bash
# Run the full stack (app on :8080 + PostgreSQL 16, Flyway migrates on start).
# On first start the `demo` profile seeds a realistic fake dataset so the whole monthly cycle
# (import → generate → approve → export) is demonstrable offline — see docs/runbook.md.
docker compose up --build
# → http://localhost:8080 , health at http://localhost:8080/actuator/health

# Build and test (Testcontainers spins up PostgreSQL 16 — Docker must be running)
./mvnw verify
```

On macOS with **Colima** (not Docker Desktop), Testcontainers needs the daemon socket pointed
out explicitly before `./mvnw verify`:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
```

Vendored front-end assets (served locally, no CDN): Digdir
[Designsystemet](https://github.com/digdir/designsystemet) CSS + theme (MIT) and
[htmx](https://htmx.org) (0BSD), under `src/main/resources/static/`.

## Key dates

| Date | Milestone |
|---|---|
| 2026-08-10 | CRM decision meeting (D365 as customer source vs temporary registry) |
| 2026-12 | Full dry-run of the pipeline with test data |
| 2027-01-01 | New price model in force; measurement/data collection starts |
| early 2027-02 | First production run: invoice January 2027 usage |

## Documentation

| Doc | Contents |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Instructions for the implementing agent — start here |
| [docs/01-context.md](docs/01-context.md) | Business context, actors, decisions, glossary |
| [docs/02-architecture.md](docs/02-architecture.md) | Stack, modules, portability rules |
| [docs/03-datamodel.md](docs/03-datamodel.md) | Flyway schema and design decisions |
| [docs/04-phases.md](docs/04-phases.md) | Build phases with acceptance criteria |
| [docs/05-testing.md](docs/05-testing.md) | Test strategy, golden-file contract |
| [docs/06-future.md](docs/06-future.md) | Roadmap beyond MVP and how the MVP prepares for it |
| [docs/07-open-questions.md](docs/07-open-questions.md) | Open questions, owners, design hedges |
| [docs/08-traceability.md](docs/08-traceability.md) | Requirements traceability — every stated requirement mapped to features |
| [docs/09-beslutningslogg.md](docs/09-beslutningslogg.md) | **Decision log** — all decisions and correspondence, chronological (Norwegian) |
| [docs/10-presentasjon.md](docs/10-presentasjon.md) | **Presentation** — status, diagrams, roadmap for stakeholders (Norwegian) |
| [docs/brukerveiledning.md](docs/brukerveiledning.md) | **User guide** — step-by-step onboarding for end users (Norwegian) |
| [docs/funksjonsoversikt.md](docs/funksjonsoversikt.md) | **Feature inventory** — every function with a stable ID, for cross-referencing requirements |
| [docs/runbook.md](docs/runbook.md) | Ops runbook — monthly procedure, re-run, DFØ errors, local demo |
| [docs/csv-format.md](docs/csv-format.md) | Usage-import CSV contract |

## Relationship to the old system

The repository `../agresso` (kept read-only) is the current production tool operated by FEL
for the older services (digital postkasse, e-signering, Altinn monthly, BOD). It keeps
running unchanged. This project reuses its most valuable artifact — the LG04 fixed-width
writer and its golden-file tests — and replaces everything around it.
