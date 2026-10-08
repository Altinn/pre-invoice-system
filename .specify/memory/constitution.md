<!--
Sync Impact Report
- Version change: template → 1.0.0 (initial ratification; principles derived from CLAUDE.md hard rules)
- Principles added: I–VII (all new)
- Added sections: Delivery Constraints, Development Workflow, Governance
- Templates: .specify/templates/plan-template.md ✅ (generic "Constitution Check" gate is compatible);
  spec-template.md ✅; tasks-template.md ✅ (no new mandatory task types beyond tests + docs)
- Deferred TODOs: none
-->
# forsystem Constitution

## Core Principles

### I. Never Invent Business Values
Unknown accounting codes, dimensions, artikkel-ids, prices, product mappings and data formats are
open questions with owners in `docs/07-open-questions.md`. Code MUST use loud, clearly fake
placeholders marked `TODO(OQ-<n>)`, never plausible guesses. A wrong-but-plausible value is worse
than a loud placeholder because it reaches an invoice unnoticed.

### II. LG04 Output Is Contractual
LG04 lines are 4324 characters, fixed byte offsets, Windows-1252 written directly. Golden-file
tests are the specification: output MUST be byte-identical or the build fails.

### III. Traceability Is Structural
Every invoice line MUST be traceable to the usage row, price and run that produced it; history is
never rewritten. Imported source data MUST be retained (archived with checksum) so a run can be
explained and recomputed later.

### IV. Explicit Billing Period
The billing period is always an explicit parameter (first day of the month). It MUST NOT be derived
from the current date inside domain logic or stored in static state. Schedulers may compute a
period, but pass it explicitly.

### V. Portability Behind Ports
External systems (datavarehus, CRM, file archive, auth, delivery) sit behind ports. Azure SDK
imports are allowed only in `adapter` packages. The app MUST run fully locally with
`docker compose up` and `mvn verify` MUST need no external services.

### VI. Secrets From Configuration Only
Secrets come from environment/config (or platform identity). Shelling out for secrets is banned.
Key Vault and workload identity are deployment concerns, not domain code.

### VII. Humans Approve Money
Automation may fetch, validate, stage and notify, but committing data that changes what is invoiced
and approving runs MUST remain an explicit, audited human action by an authorised role.

## Delivery Constraints

- Java 21, Spring Boot 3.5.x, Maven, PostgreSQL 16 with Flyway; Thymeleaf + htmx UI; REST under `/api`.
- Domain and database naming is Norwegian (matching the FinMod data-element sheet); general code
  identifiers are English.
- First production run early February 2027 (January 2027 usage); a full dry-run must be possible in
  December 2026. Features that threaten this date are cut, not rushed.

## Development Workflow

- Every change ends with `mvn verify` green, docs updated when behaviour changes, and one
  conventional commit per logical change.
- Tests use fixtures with fake organisation numbers; real customer data never enters the repo.
- The reference repository `../agresso` is read-only.

## Governance

This constitution summarises `CLAUDE.md`; where they conflict, `docs/07-open-questions.md` wins,
then `CLAUDE.md`, then this file. Amendments update this file with a Sync Impact Report and a
semantic version bump (MAJOR: principle removed/redefined; MINOR: principle added; PATCH: wording).
Every plan's "Constitution Check" MUST evaluate principles I–VII.

**Version**: 1.0.0 | **Ratified**: 2026-10-06 | **Last Amended**: 2026-10-06
