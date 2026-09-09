# CLAUDE.md — instructions for the implementing agent

## Mission

Build the MVP of **forsystem** — Digdir's invoicing pre-system for Altinn products under the
FinMod price model. It replaces a manual process (Excel registries, psql imports, hand-run
batch jobs) with one web application: maintain registries, import usage, generate invoice
basis (fakturagrunnlag), run controls, approve, and export files (LG04 for Unit4 via DFØ,
PDF detail views, CSV/XLSX).

Hard deadline: **first production run early February 2027**, invoicing January 2027 usage.
Measurement starts 2027-01-01. A full dry-run must be possible in December 2026.

Work in phases (docs/04-phases.md), strictly in order. Do not start a phase before the
previous one meets its acceptance criteria.

## Read first, in this order

1. `docs/01-context.md` — business context, actors, decisions already made, glossary
2. `docs/02-architecture.md` — stack, module layout, portability rules
3. `docs/03-datamodel.md` — Flyway schema (the authoritative sketch — start from this)
4. `docs/04-phases.md` — phase scope and acceptance criteria
5. `docs/05-testing.md` — test strategy; the golden-file contract for LG04
6. `docs/06-future.md` — what comes later; build the seams, not the features
7. `docs/07-open-questions.md` — unresolved facts; NEVER invent answers to these

## Hard rules

- **The reference repo at `../agresso` is READ-ONLY.** It is the production system still
  operated by FEL for the older services (digital postkasse, e-signering, Altinn, BOD).
  Never modify it. Use it as reference for: the LG04 fixed-width line format
  (`src/main/java/no/difi/faktura/Linje1|2|3.java`), the golden-file tests
  (`src/test/java/no/difi/*Test.java`), and the operational notes in its README.
- **Never invent business values.** Unknown konto/dimensions/artikkel-ids/prices/SMS formats
  are open questions with owners (docs/07). Use clearly fake placeholders and mark them
  `TODO(OQ-<n>)` referencing docs/07. A wrong-but-plausible accounting code is worse than a
  loud placeholder.
- **LG04 output is contractual.** 4324-character lines, fields at fixed byte offsets,
  Windows-1252 encoding written directly (no iconv step). Golden-file tests are the spec;
  byte-identical or it fails.
- **Portability**: no Azure SDK imports outside `adapter` packages (see docs/02). The app
  must run fully locally with `docker compose up` (app + PostgreSQL), file archive on local
  disk, and auth stubbed or against any OIDC provider.
- **Secrets** come from environment/config only. Never shell out for secrets (the old repo's
  `op read` pattern is explicitly banned). Azure Key Vault is an optional deployment concern,
  not application code.
- Domain and database naming is **Norwegian** (fakturakjoring, bruksdata, kunde…) to match
  the FinMod data-element sheet; general code identifiers are English.
- Billing period is always an **explicit parameter** (first day of month), never derived from
  the current date, and never stored in static state. This was a defect class in the old repo.
- Every phase ends with: all tests green (`mvn verify`), docs updated if behavior changed,
  a conventional commit. Do not batch multiple phases into one commit.

## Build/run expectations

- Java 21 (LTS), Spring Boot 3.5.x, Maven. GroupId `no.digdir`, artifactId `forsystem`.
- `docker compose up` → application + PostgreSQL 16, Flyway migrates on start.
- `mvn verify` → unit + integration tests (Testcontainers PostgreSQL), no external services.
- Web UI: server-rendered (Thymeleaf + htmx), Digdir Designsystemet CSS. REST under `/api`.

## When something is ambiguous

Prefer the decision recorded in docs/. If docs conflict with this file, docs/07 (open
questions) wins over everything — it is the most recently maintained. If genuinely blocked,
stop and ask; do not guess around a blocking unknown.
