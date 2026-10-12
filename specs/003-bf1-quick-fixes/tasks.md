---
description: "Task list for the BF1 quick fixes"
---

# Tasks: Small fixes from Økonomi's BF1 findings

**Input**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui.md](./contracts/ui.md)

**Tests**: Included. Write each story's tests first and watch them fail.

**Paths**: `M` = `src/main/java/no/digdir/forsystem`, `R` = `src/main/resources`,
`T` = `src/test/java/no/digdir/forsystem`

There is no setup or foundational phase: no migration and no shared prerequisite. The stories are
independent and are delivered in plan order, one commit each.

## Phase 1: US1 + US2 — price versions (F06, F05) (P1)

**Commit**: `feat(registry): price versions list only products that need a price and explain their dates`

- [x] T001 [P] [US1] Extend `T/registry/PrisversjonServiceIT.java` for `produkterFor`:
  - an inactive product with no price is absent
  - an inactive product with a price is present
  - all active products are present, ordered by code
- [x] T002 [P] [US2] Add tests for `dekning` (unit test `T/registry/PrisdekningTest.java`):
  - from 2027-01-01, open → first 2027-01, open-ended
  - 2027-01-15 → 2027-12-31 → first 2027-02, last 2027-12
  - 2027-01-15 → 2027-01-31 → covers nothing
  - 2027-03-01 → 2027-03-01 → exactly 2027-03
- [x] T003 [US2] Create the record `M/registry/Prisdekning.java` (`Periode forste`, `Periode siste` (nullable = open), `boolean ingen()`, `String tekst()` returning the Norwegian sentence from contracts/ui.md) with a static `fra(LocalDate gyldigFra, LocalDate gyldigTil)` that implements the data-model.md rule.
- [x] T004 [US1] Add `public List<Produkt> produkterFor(Long versjonId)` and `public Prisdekning dekning(Prisversjon v)` to `M/registry/PrisversjonService.java`.
- [x] T005 [US1] In `M/web/PrisversjonController.java` `detalj`, use `produkterFor(id)` and add `dekning` to the model.
- [x] T006 [US1] In `R/templates/prisversjon/detalj.html`:
  - tag inactive products with a neutral `ds-tag` "inaktiv"
  - show `dekning.tekst()` under the validity line
- [x] T007 [US2] In `R/templates/prisversjon/liste.html`, add the help text from contracts/ui.md under the date fields (`ds-paragraph` size sm).
- [x] T008 Run `./mvnw verify` green, then commit.

## Phase 2: US3 — product list (F03, F01) (P2)

**Commit**: `feat(registry): product list shows active first and dimension columns`

- [x] T009 [P] [US3] Add a test for `oversikt()`: with melding active, studio inactive and varsling active, the order is melding, varsling, studio (existing registry IT, or a new `T/registry/ProduktServiceIT.java`).
- [x] T010 [P] [US3] Add a unit test `T/web/DimensjonsnavnTest.java`: `etikett(1)` is "Dim 1" when empty and "Dim 1 (koststed)" when set; blank counts as empty.
- [x] T011 [US3] Add `public List<Produkt> oversikt()` to `M/registry/ProduktService.java`: active first, then by code.
- [x] T012 [US3] Create `M/web/Dimensjonsnavn.java`: `@ConfigurationProperties("forsystem.produkt.dimensjoner")` record `(String dim1, String dim2, String dim4)` with `etikett(int n)`. Add the `forsystem.produkt.dimensjoner.dim1/2/4: ${PRODUKT_DIMn_NAVN:}` entries to `R/application.yaml` with a comment pointing at OQ-2.
- [x] T013 [US3] In `M/web/ProduktController.java`, use `oversikt()` in `liste` and add `dimensjoner` to the model in `liste` and `rediger`.
- [x] T014 [US3] In `R/templates/produkt/liste.html`, add Dim columns (`?: '—'`) and tag inactive rows. In `R/templates/produkt/rediger.html`, use the labels.
- [x] T015 Run `./mvnw verify` green, then commit.

## Phase 3: US4 — no-usage warning (C-09) (P1)

**Commit**: `feat(billing): warn when an active customer has no usage in the period`

- [ ] T016 [US4] Extend `T/billing/GenereringControlsIT.java`:
  - active customers A and B, usage only for A → exactly one `AKTIV_KUNDE_UTEN_BRUK` naming B, with ADVARSEL severity, and approval succeeds
  - an INAKTIV customer with no usage → no finding
  - a customer whose usage hit MANGLER_KUNDENUMMER → no no-usage finding
- [ ] T017 [US4] Add `AKTIV_KUNDE_UTEN_BRUK` under ADVARSEL in `M/billing/Kontrollkode.java`.
- [ ] T018 [US4] In `M/billing/FakturakjoringService.java` `bygg()`, after the usage loop, add one `advarsel(kjoringId, null, AKTIV_KUNDE_UTEN_BRUK, "Kunde <navn> (<orgnr>) har aktiv avtale, men ingen bruk i perioden")` for each customer with `avtalestatus = AKTIV` whose orgnr has no row in `bruk`, in orgnr order.
- [ ] T019 Check that existing tests that assert exact finding lists still hold (`grep -rn "findByKjoringId\|funn(" src/test`). Adjust them, keeping their intent.
- [ ] T020 Run `./mvnw verify` green, then commit.

## Phase 4: US5 — export-file download logging (C-08) (P2)

**Commit**: `feat(export): log export-file downloads and check they belong to the run`

- [ ] T021 [US5] Extend `T/billing/EksportAvstemmingIT.java` (it already has MockMvc and an exported run):
  - downloading the LG04 twice gives two `LASTET_NED` rows with `"type": "LG04"`
  - the file id of another run's file gives a 400 and no new `LASTET_NED`
- [ ] T022 [US5] Add `@Transactional public Nedlasting lastNedEksportfil(Long kjoringId, Long filId)` to `M/export/EksportService.java`:
  - load the file
  - if `fil.kjoringId()` differs, throw `Regelbrudd("Fant ikke eksportfil " + filId + " for kjøring " + kjoringId)`
  - read the archive, log `LASTET_NED` with periode, type and filnavn, and return a `Nedlasting` with the media type per file type
- [ ] T023 [US5] In `M/web/FakturakjoringController.java` `lastNed`, delegate to the service. Remove the controller's now-unused `mediatype` helper and the `FileArchive` field if nothing else uses them.
- [ ] T024 Run `./mvnw verify` green, then commit.

## Phase 5: Docs

**Commit**: `docs: document BF1 quick fixes (003)`

- [ ] T025 [P] `docs/brukerveiledning.md`:
  - §4 Produkter: order and Dim columns
  - §5 Prisversjoner: what the dates mean, with the mid-month example, and which products are shown
  - §11 Kontrollfunn: a new row for `AKTIV_KUNDE_UTEN_BRUK`
  - §9 Hendelseslogg: downloads are logged
- [ ] T026 [P] `docs/funksjonsoversikt.md`:
  - new rows F-REG-PRIS-09 (only products that need a price), F-REG-PRIS-10 (coverage text), F-REG-PROD-06 (order and dimensions), F-CTRL-10 (`AKTIV_KUNDE_UTEN_BRUK`), F-EXP-16 (download logging and ownership check)
  - update the test count
- [ ] T027 [P] `docs/runbook.md`: document `PRODUKT_DIM1_NAVN`/`PRODUKT_DIM2_NAVN`/`PRODUKT_DIM4_NAVN`, which are set per environment once Økonomi names the dimensions (OQ-2).
- [ ] T028 Run `./mvnw verify` green, walk through quickstart.md on a separate compose project, then commit.

## Dependencies

All four code phases are independent of each other. They are serialized only to give one commit per
story. The docs phase comes last.

## Parallel opportunities

T001 with T002, T009 with T010, and T025–T027.
