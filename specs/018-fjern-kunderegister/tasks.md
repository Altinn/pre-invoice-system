# Tasks: Remove the customer registry — customer data comes from the DWH

**Input**: Design documents from `/specs/018-fjern-kunderegister/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/ui-and-import.md](contracts/ui-and-import.md), [quickstart.md](quickstart.md)

**Tests**: included. The constitution requires `mvn verify` green, and most existing billing ITs build
customers through the registry, so the test changes are part of the work, not an add-on.

**Paths**: `M/` = `src/main/java/no/digdir/forsystem/`, `T/` = `src/test/java/no/digdir/forsystem/`,
`R/` = `src/main/resources/`.

**Story order**: US1 and US2 are both P1 and must ship together. US2 is implemented **first**,
because removing the registry (US1) would break generation until it reads DWH data. The phases below
follow that build order. Spec story labels are kept.

**Gate before merging**: decision D3 (own CSV format with customer columns) is confirmed with Økonomi
(plan «Open items»). If it is rejected, T009 and T010 change to «own format for tests/demo only».

## Phase 1: Setup

- [ ] T001 (NOT RUN: no tt02 access from the dev machine; recorded under OQ-28) Before writing V17, query a tt02 copy (or the demo database) for customers with more than one `kundenummer_regel`, product-specific `kunde_referanse` rows (`produkt_id is not null`), or `fakturamottaker_orgnr` set. Record the count in `specs/018-fjern-kunderegister/research.md` under R3. If any exist, add them to OQ-28 in T040 (no code)

---

## Phase 2: Foundational (blocks all stories)

**Purpose**: store customer values per import, and give invoices their own orgnr. The registry is
still in place at the end of this phase, and all existing tests still pass.

- [X] T002 Write `R/db/migration/V17__kundedata_fra_dwh.sql`, part 1 only: create `bruksdata_kunde` per [data-model.md](data-model.md) (`unique (import_id, organisasjonsnummer)`, `motstridende text[] not null default '{}'`, index on `import_id`); add `faktura.organisasjonsnummer`, backfill from `kunde` via `kunde_id`, backfill `faktura.kundenavn` from `kunde.virksomhetsnavn` where NULL, then set both NOT NULL; make `faktura.kunde_id` and `faktura_under_terskel.kunde_id` **nullable** (so generation can stop writing them in T020), and add `unique (kjoring_id, organisasjonsnummer, kundenummer)` on both tables. Backfill `bruksdata_kunde` for existing VALIDERT imports from the registry (one row per distinct orgnr in the import's `bruksdata`, with the customer's default reference and its kundenummer rule without product; leave columns NULL when there is no customer), so a regenerated old period does not lose customers. Do **not** drop anything yet (that is T027). Put a header comment pointing at specs/018 D1/D5
- [X] T003 [P] Create `M/usage/BruksdataKunde.java` (Spring Data JDBC record for `bruksdata_kunde`) and `M/usage/BruksdataKundeRepository.java` with `findByImportId(Long)`
- [X] T004 [P] Create `M/usage/Kundedata.java`: record `(organisasjonsnummer, navn, kundenummer, fakturareferanse, bestillingsnummer, List<String> motstridende)` plus a static collector class `Kundedata.Samler` with `legg(orgnr, navn, kundenummer, fakturareferanse, bestillingsnummer)` and `List<Kundedata> resultat()`. Rule (research D2): blanks are ignored; the first non-blank value per field is kept; every further distinct non-blank value of kundenummer, fakturareferanse or bestillingsnummer adds `"<felt>: v1 | v2 …"` to `motstridende`; a different name is **not** a conflict (keep the first). Output is in first-seen orgnr order
- [X] T005 [P] Unit test `T/usage/KundedataTest.java`: one value; blank + value → value, no conflict; two kundenummer → conflict text lists both in order; name differences ignored; three fields in conflict → three entries
- [X] T006 Add `organisasjonsnummer` to `M/billing/Faktura.java` (keep `kundeId` for now) and set it in `FakturakjoringService.bygg` from `data.kunde.organisasjonsnummer()` (`M/billing/FakturakjoringService.java` ~`:418`); fix every `new Faktura(` call site in main and test code
- [X] T007 Replace live registry reads after generation with the invoice's own columns in `M/billing/FakturakjoringService.java`: `kundenavn()` (~`:746-757`, drop the «(nåværende navn)» fallback), `utenFakturareferanse` (~`:708`) and `grunnlagslinjer` (~`:782`) use `faktura.organisasjonsnummer()`; same for any `kunder.findById` in `M/export/EksportService.java` and `M/web/Fakturautvalg.java`
- [X] T008 Migration IT `T/billing/KundedataMigreringIT.java`: run Flyway to V16 (`target("16")`), insert a customer with a rule and a default reference, a VALIDERT import with usage for it, a run with an invoice whose `kundenavn` is NULL, and a `faktura_under_terskel` row; migrate to V17; assert `faktura.organisasjonsnummer` and `kundenavn` are filled and `bruksdata_kunde` holds the backfilled values

**Checkpoint**: `mvn verify` green; behaviour unchanged.

---

## Phase 3: User Story 2 — Invoices take customer data from the imported usage (P1)

**Goal**: imports store customer values; generation uses them instead of the registry.

**Independent test**: import a file where org A has kundenummer `K-A` and fakturareferanse `REF-A`,
with no `kunde` row. Generate. The invoice and LG04 carry `K-A`/`REF-A` (spec US2 #1, SC-003).

### Import side

- [X] T009 [P] [US2] Extend `M/usage/RaaBruksrad.java` with nullable `kundenavn, kundenummer, fakturareferanse, bestillingsnummer` (keep a constructor without them for existing callers)
- [X] T010 [US2] In `M/usage/CsvUploadUsageSource.java`, read the four optional columns by header name (contract [ui-and-import.md](contracts/ui-and-import.md) «Own CSV format»). Absent columns → null. Required columns are unchanged
- [X] T011 [P] [US2] Extend `M/usage/DwhRad.java` with `navn` and `bestillingsnummer` (update the 7-arg convenience constructor and the javadoc: no longer «cross-check only»); in `M/usage/DwhRespons.java` read `organisation_name` and `bestillingsnummer` as optional text
- [X] T012 [US2] In `M/usage/DwhCsv.java`, replace `KundeIFil` and the `kunder.putIfAbsent` logic (~`:124, :195, :256-261`) with `Kundedata.Samler` fed from **used** rows only. `Fil.kunder()` becomes `List<Kundedata>`. Remove the static `kunder(byte[])` helper if it has no caller left after T024
- [X] T013 [US2] Add `List<Kundedata> kunder` to `M/usage/Forhaandsvisning.java` (default `List.of()` in the short constructor; carry it through `medFil`), plus helpers `antallMotstridende()` and `utenKundenummer()`
- [X] T014 [US2] Fill `kunder` in every preview path: `UsageImportService.forhaandsvis` (own CSV, from `RaaBruksrad` via `Kundedata.Samler`, valid rows only) in `M/usage/UsageImportService.java`, and `DwhImportService.fraFil` / `bygg` (file and API, from used `DwhRad`s) in `M/usage/DwhImportService.java`, including the staged path `forhaandsvisStaget`
- [X] T015 [US2] Persist `fv.kunder()` as `bruksdata_kunde` rows in `UsageImportService.importer` (same transaction, next to `lagreKilder`) in `M/usage/UsageImportService.java`; add `kundedata(Long importId)` for readers
- [X] T016 [US2] Preview lines per contract: conflicts and missing kundenummer in `R/templates/bruksdata/forhandsvis.html` as plain summary lines with `<details>` lists (orgnr, name, values). Never blocking
- [X] T017 [P] [US2] Tests: `T/usage/DwhCsvFlaggTest.java` (conflict across used rows; excluded/DPV rows do not contribute), `T/usage/DwhResponsTest.java` (name/bestillingsnummer read, absent → null), `T/usage/UsageImportServiceIT.java` (own CSV with and without customer columns → `bruksdata_kunde` rows; conflict recorded)

### Generation side

- [X] T018 [US2] Add `MOTSTRIDENDE_KUNDEDATA(BLOKKERENDE, …)` to `M/billing/Kontrollkode.java`, with a definition that points to CRM; rewrite the `MANGLER_KUNDENUMMER` definition («Datavarehuset har ikke levert kundenummer for organisasjonen …») and the `MANGLER_FAKTURAREFERANSE` text (drop «legg inn referansen i kunderegisteret», point to CRM + new import). Mark `MANGLER_KUNDE`, `INAKTIV_AVTALE`, `AKTIV_KUNDE_UTEN_BRUK` with `@Deprecated` and a boolean `utgatt()` (research D6); keep their definitions
- [X] T019 [US2] Rewrite the customer part of `FakturakjoringService.bygg` (`M/billing/FakturakjoringService.java` ~`:243-500`): load `bruksdata_kunde` for the run's import (`ctx`'s `bruksdata_import_id`; for correction runs the import the differences come from) into `Map<orgnr, BruksdataKunde>`; per usage row: conflict → collect for MOTSTRIDENDE_KUNDEDATA (one finding per orgnr, listing `motstridende`, skipped row ids linked); blank kundenummer → MANGLER_KUNDENUMMER once per orgnr («Datavarehuset har ikke kundenummer for <orgnr> (<navn>)»), rows linked; otherwise the reference pair from `Referansepar.av(fakturareferanse, bestillingsnummer)` (keep the fallback), `servicekode` NULL. Remove MANGLER_KUNDE, INAKTIV_AVTALE, AKTIV_KUNDE_UTEN_BRUK generation, `velgRegel`, `velgReferanse` and the three registry loads
- [X] T020 [US2] In the same file: `Gruppe` becomes `(organisasjonsnummer, kundenummer)`; `Gruppedata` holds `BruksdataKunde` instead of `Kunde`; the threshold (`kundesum`), `sumPerKunde`, `forsteFakturaPerKunde` and `leggTilForrigePeriodeAdvarsler` key on orgnr (compare with `Faktura.organisasjonsnummer()` of the previous run); `Faktura` gets name from `bruksdata_kunde.virksomhetsnavn` (fallback: orgnr, never invented), `tilleggstekst` NULL, `fakturamottaker_orgnr` = orgnr; `FakturaUnderTerskel` built from the same values. Remove `kundeId` from `Faktura`/`FakturaUnderTerskel` (the column is nullable since V17, dropped in V18 by T027)
- [X] T021 [US2] Rewrite `T/billing/BillingFixture.java` (~`:38, 82-95`): instead of `Kunde`/rules/references, create the import's `bruksdata_kunde` rows (helper `kunde(orgnr, navn, kundenummer, fakturareferanse)` attached to the fixture's import). Update the billing ITs that use it; delete or rewrite cases for product-specific references, several kundenummer per orgnr, tilleggstekst, fakturamottaker and the three retired controls (`T/billing/GenereringControlsIT.java`, `FakturareferanseIT.java`, `TerskelverdiIT.java`, `KorrigeringskjoringIT.java`, `E2eScenarioIT.java`, others as the compiler shows)
- [X] T022 [US2] New cases in `T/billing/GenereringControlsIT.java`: values from `bruksdata_kunde` reach `faktura`, `fakturalinje` and LG04 (SC-003); missing kundenummer → one MANGLER_KUNDENUMMER per orgnr, linked rows, approval refused; conflict → MOTSTRIDENDE_KUNDEDATA, no invoice for that orgnr; empty bestillingsnummer → fallback; a newer import for the period changes values on regeneration, an approved run keeps its own
- [X] T023 [US2] Run the LG04 golden-file tests (`T/export/lg04/*`, `T/billing/EksportIT.java`) and keep `src/test/resources/lg04/*` **byte-identical**; adapt only fixture setup if they fail

**Checkpoint**: generation no longer reads `kunde*`. Grep `M/billing` and `M/export` for `KundeRepository|KundenummerRegel|KundeReferanse` → no hits.

---

## Phase 4: User Story 1 — Import usage without customer maintenance (P1)

**Goal**: no way to create or edit customers; no registry comparisons.

**Independent test**: upload the fixture that gave «Opprett 7 kunder fra fila»; no notice, no button,
no «kunderegisteret har …» warnings; POST to the old route → 404 (spec US1).

- [X] T024 [US1] In `M/usage/DwhImportService.java` delete `manglendeKunder`, `opprettManglendeKunder`, `Kundeoppretting`, `kryssjekkKunderegister` (~`:162-197, :244, :267-308`) and the registry repositories it injects; drop the merge of its warnings in `M/usage/UsageImportService.java` (~`:101`)
- [X] T025 [US1] In `M/web/BruksdataController.java` delete POST `/bruksdata/last-opp/opprett-kunder` (~`:142-147`) and the `manglendeKunder`/`kundeoppretting` model attributes (~`:150-153`); in `R/templates/bruksdata/forhandsvis.html` delete the notice and button block (~`:19-35`) and the registry text at ~`:27-29`; in `R/templates/bruksdata/liste.html` delete the hint (~`:51`)
- [X] T026 [US1] Delete `M/web/KundeController.java` and `R/templates/kunde/`; delete `M/registry/Kunde.java`, `KundeReferanse.java`, `KundenummerRegel.java`, their repositories, `Avtalestatus.java`, `KundeService.java`, `BrregOppslag.java`, `BrregHttpOppslag.java`, `PermissivBrregOppslag.java`; remove BRREG config keys from `R/application*.yaml` and `.env.example` if any
- [X] T027 [US1] Add `R/db/migration/V18__dropp_kunderegister.sql`: drop `kunde_id` (with the old uniques) from `faktura` and `faktura_under_terskel`, then drop `kunde_referanse`, `kundenummer_regel`, `kunde`. Separate migration so V17 stays the safe, additive step. Extend `T/billing/KundedataMigreringIT.java` to migrate to V18 and assert the tables are gone and old invoices still read
- [X] T028 [US1] Update `M/demo/Demodata.java` (~`:56-62, 90-115`): no customer seeding; the demo usage import carries customer values (own CSV columns or `bruksdata_kunde` rows) with loud fake kundenummer (`KN-1001` style) and references
- [X] T029 [US1] Delete `T/registry/KundeServiceIT.java`, `T/registry/KundeBrregIT.java` and the customer cases in `T/registry/RegistryRepositoryIT.java`; fix `T/usage/DwhImportServiceIT.java`
- [X] T030 [US1] Rewrite the create-from-file cases in `T/usage/DwhFilImportIT.java` (~`:40-81`) and `T/usage/DwhFlaggImportIT.java`: preview HTML has no «kunderegisteret» text and no create button; POST `/bruksdata/last-opp/opprett-kunder` and GET `/kunder/1` → 404; import succeeds
- [X] T031 [US1] Retired codes still render: in `T/billing/KontrollkodeTest.java` assert the three retired codes keep a definition and `utgatt()`; in a web IT, a run with a stored `INAKTIV_AVTALE` finding renders its Kontroller tab (`R/templates/kjoring/kontroller.html`)

**Checkpoint**: `grep -rn "kunderegister\|KundeService\|/kunder" src/main` returns only help texts (handled in US3).

---

## Phase 5: User Story 3 — Help, docs and controls describe the new reality (P2)

**Goal**: no user guidance points to customer maintenance in forsystem.

**Independent test**: the learning-center build checks pass (links, a «Slik løser du det» text for
every code); no «Kunder» section.

- [X] T032 [P] [US3] Delete `R/hjelp/slik-gjor-du/opprett-kunder-fra-fila.md`, `legg-til-fakturareferanse.md`, `legg-til-kundenummerregel.md`, `avvikende-fakturamottaker.md`, and the «Kunder» section in `R/hjelp/meny.yml` (~`:19-22, 56`)
- [X] T033 [US3] Update `R/hjelp/kontrollfunn.yml` (~`:5-27`): «Slik løser du det» for MANGLER_KUNDENUMMER, MANGLER_FAKTURAREFERANSE and the new MOTSTRIDENDE_KUNDEDATA (correct in CRM, wait for the DWH, import again, regenerate); the three retired codes get «Kontrollen kjøres ikke lenger (specs/018). Funnet gjelder en kjøring fra før endringen.»
- [X] T034 [P] [US3] Rewrite registry mentions in `R/hjelp/manedsrutinen/1-sjekk-registrene.md` (no customer step, SC-005), `2-hent-bruksdata.md` (~`:45`), `3-generer.md`, `4-kontrollfunn.md`, `forsta-systemet/finmod-kort-fortalt.md` (~`:36`), `forsta-systemet/slik-grupperes-fakturaer.md` (one invoice per orgnr), `forsta-systemet/terskelverdi.md`, `forsta-systemet/sporbarhet.md`
- [X] T035 [P] [US3] Update `R/hjelp/oppslag/roller.md` (~`:30`, drop the customer row), `oppslag/ordliste.md`, `oppslag/statuser.md` (avtalestatus), `oppslag/kontrollfunn.md`, `oppslag/csv-format.md` (four optional columns), `kom-i-gang/rundtur.md`, `kom-i-gang/prov-selv.md`, `kom-i-gang/hva-er-forsystem.md`, `slik-gjor-du/last-opp-csv.md`, `slik-gjor-du/finn-en-faktura.md`, and the other `R/hjelp` files the grep `kunderegist` still finds
- [X] T036 [US3] Fix the remaining UI text in `R/templates/kjoring/kontroller.html` (~`:125-136`, «Legg inn referansen på kunden») and anywhere `grep -rn "kunderegist" R/templates` finds

---

## Phase 6: Polish & cross-cutting

- [X] T037 [P] `docs/03-datamodel.md`: remove V2 registry sections (~`:14-16, 66-97, 151-158, 323-330, 352`), add `bruksdata_kunde`, `faktura.organisasjonsnummer`, V17/V18
- [X] T038 [P] `docs/01-context.md` (~`:46`) and `docs/06-future.md` §2 (~`:22-45`): CRM → DWH is the customer path; a direct CRM import is no longer planned
- [X] T039 [P] `docs/09-beslutningslogg.md`: new entry superseding B-14 (registry as cache/fallback) with the ticket's decision and the two clarifications
- [X] T040 `docs/07-open-questions.md`: OQ-5 answered (interface = DWH, registry removed in specs/018); new OQ-28 (product-specific references, several kundenummer per orgnr with servicekode/tilleggstekst, avvikende fakturamottaker; owner Økonomi + CRM/DWH) including T001's findings; update OQ-7, OQ-10, OQ-13, OQ-14, OQ-15, OQ-24 «current handling» columns; update the assumptions list (~`:43-45`)
- [X] T041 [P] `docs/funksjonsoversikt.md`: remove F-REG-KUN-01…12 (~`:53-64`), update F-USG-23 (~`:91`) and N-03 (~`:233`); `docs/08-traceability.md` K-02 (~`:24`); `docs/csv-format.md` optional customer columns and the DWH customer fields' new role; `docs/runbook.md` the `pg_dump` step before V18 ([quickstart.md](quickstart.md))
- [X] T042 Full `mvn verify` (Colima `DOCKER_HOST`), then the manual steps in [quickstart.md](quickstart.md) with `docker compose up`; screenshot the preview for the PR
- [X] T043 Conventional commits per phase (`feat(usage)`, `feat(billing)`, `refactor!: remove customer registry`, `docs(hjelp)`, `docs`), each ending with the `Signed-off-by` line

---

## Dependencies & execution order

- Phase 1 → Phase 2 → US2 (Phase 3) → US1 (Phase 4) → US3 (Phase 5) → Polish.
- US2 before US1: US1 deletes the code that generation uses until T019/T020 land.
- US3 can start once T018 has fixed the code names (help texts reference them); its file edits do not
  touch Java.
- Within US2: T009–T016 (import) before T019–T020 (generation); T021 right after T020 (the build
  breaks until the fixture compiles).
- T027 (V18) after T026: no Java may reference the tables.

## Parallel opportunities

- Phase 2: T003, T004, T005 together.
- US2: T009 and T011 together; T017 alongside T018.
- US3: T032, T034, T035 together (different files).
- Polish: T037, T038, T039, T041 together.

## Implementation strategy

There is no smaller releasable MVP: US1 alone would break invoicing, and US2 alone leaves the
contradictory button in place. Deliver Phases 2–4 as one PR (several commits), with US3 and docs in
the same PR (the constitution says help changes in the same PR as the UI). Merge before the December
2026 dry-run, after the D3 gate.
