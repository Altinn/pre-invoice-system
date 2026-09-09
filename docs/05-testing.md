# 05 — Test strategy

## Principles

- **The LG04 golden files are the spec.** The old repo locks the format character-by-
  character (`Linje1Test/Linje2Test/Linje3Test` in `../agresso`); port them and keep the
  discipline: any format change must be a conscious golden-file update in its own commit.
- **Business logic tests never touch the clock.** Period is a parameter everywhere; tests
  pass explicit periods. If you need "previous month", the test constructs it.
- **Integration over mocks for persistence.** Testcontainers PostgreSQL for everything
  that touches SQL; no H2 (dialect lies).
- Determinism: engine output ordering is defined (kunde orgnr, kundenummer, produkt kode) —
  tests compare full structures, not spot fields.

## Test pyramid

| Level | Tooling | What |
|---|---|---|
| Unit | JUnit 5 | Engine math (rounding half-up, øre conversion ×100 only in export), period type, kundenummer rule resolution, CSV row parsing |
| Golden files | JUnit 5 + resources | LG04: single-line invoice (ported, byte-identical), multi-line invoice, Windows-1252 bytes for æ/ø/å, 4324-char invariant, field-offset spot checks |
| Repository/integration | Testcontainers PostgreSQL 16 | Flyway migrations from scratch; every repository; constraint behavior (partial unique index on runs, nulls-not-distinct uniques, re-import replacement) |
| Web/MVC | MockMvc | Role enforcement per endpoint (LESER read-only, GODKJENNER approval), CSRF, validation error rendering |
| End-to-end scenario | SpringBootTest + Testcontainers | Fixture registries + usage CSV → import → generate → approve → export → compare LG04 to golden file, verify eksportfil + hendelseslogg rows |

## Fixture dataset (build once, reuse everywhere)

Must exercise every rule from docs/01:
- normal customer, one product;
- customer with deviating fakturamottaker;
- customer with product-specific fakturareferanse + bestillingsnummer;
- customer with two kundenummer (different servicekoder) → expect two fakturaer;
- two customers sharing a kundenummer with different tilleggstekst;
- customer with INAKTIV avtalestatus → BLOKKERENDE;
- usage for an orgnr with no kunde row → BLOKKERENDE;
- product missing price / missing kontering → BLOKKERENDE;
- SMS + Azure cost rows → pass-through lines;
- amounts that expose rounding (e.g. volume × price = x.005).

## Control-specific tests

Every kontroll gets: a test that it fires on the fixture designed for it, a test that
BLOKKERENDE prevents the GODKJENT transition, and a test that fixing the data and
re-running (FORKAST + new run) clears it.

## What we deliberately do not test in MVP

DFØ's actual import (manual, human-verified in the December dry-run with the FEL contact);
Entra ID login flow beyond config (OIDC is stubbed in `local`, smoke-tested in Azure);
performance beyond the 5 000-customer sanity check.
