# Tasks: Versioned correction runs

**Input**: Design documents from `/specs/007-korrigeringskjoring/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui.md](./contracts/ui.md)

**Tests**: Required. Financial deltas, history and LG04 are contractual; write the named tests first
and observe the relevant failure before implementation.

**Organization**: Tasks are grouped by delivery gate and user story. `[P]` means different files and
no unfinished dependency on another task in the same group.

## Phase 0: Business and fixture gates

**Purpose**: Prevent implementation from inventing the correction mechanism.

- [ ] T001 Record Økonomi + VIS's OQ-6 decision in `docs/07-open-questions.md`: negative-line versus
  credit-note mechanism, original-invoice reference, net-negative invoice, removal and operational flow.
- [ ] T002 Record FEL/VIS's accepted unique correction `batchId` and file/version naming under OQ-6/OQ-23
  in `docs/07-open-questions.md`; do not choose a format in code before this is answered.
- [ ] T003 Obtain and anonymize a complete old/new DWH fixture pair with unchanged, increase, decrease,
  addition and removal cases under `src/test/resources/fixtures/korrigering/`; retain no real customer data.
- [ ] T004 Review dismissal semantics and unchecked-by-default selection with FORVALTER users; update
  `specs/007-korrigeringskjoring/spec.md` if the decision differs from DG-3.
- [ ] T005 Add `forsystem.korrigering.eksport-enabled=false` to configuration and deployment overlays,
  documented as a hard gate; ordinary export remains enabled.

**Checkpoint**: Domain/comparison implementation may start after T003/T004. Export implementation
requiring accounting semantics waits for T001/T002.

---

## Phase 1: Foundational schema and version metadata

**Purpose**: Immutable primitives needed by every story.

- [ ] T006 Write failing migration/invariant integration tests in
  `src/test/java/no/digdir/forsystem/billing/KjoringVersjonIT.java`: historical backfill, unique
  `(periode, versjon)`, one in-flight run, coexistence of EKSPORTERT V1 + GENERERT V2.
- [ ] T007 Add `src/main/resources/db/migration/V12__korrigeringskjoring.sql` exactly from
  `data-model.md`: candidate statuses, run metadata/index replacement, comparison/change/selection
  tables, line source FK and indexes.
- [ ] T008 [P] Add `Kjoringstype` and extend `billing/Fakturakjoring.java` with type, version, previous
  run, comparison and reason; update all ordinary constructors to explicit ORDINAER/V1-compatible values.
- [ ] T009 [P] Add `usage/BruksdataSammenligning.java`, `usage/BruksdataEndring.java` and enums for
  comparison/change states; keep Norwegian persisted names.
- [ ] T010 [P] Add Spring Data repositories for comparisons, changes and correction selections in
  `usage/` and `billing/`, with paged/filter query support implemented by explicit JDBC where derived
  queries are insufficient.
- [ ] T011 Extend `usage/BruksdataImport.java` and repository queries for KANDIDAT/BEHANDLET while
  proving `gjeldendeImport` still selects only VALIDERT.
- [ ] T012 Update `billing/Fakturalinje.java` and all creation/read sites for `bruksdataEndringId`;
  ordinary lines continue to set only `bruksdataId`.
- [ ] T013 Run migration tests against empty DB and a pre-V12 fixture containing multiple periods,
  FORKASTET attempts and one active/exported run; fix without deleting history.

**Checkpoint**: V12 migrates existing data and ordinary behavior compiles/tests unchanged.

---

## Phase 2: User Story 1 — See only what changed (P1) 🎯 First demonstrable slice

**Goal**: Persist and show an exact immutable diff without changing billing.

**Independent Test**: Exported V1 + complete candidate yields only added/changed/removed rows and an
exact reconciliation; identical candidate yields no action.

### Tests

- [ ] T014 [P] [US1] Write `usage/BruksdataSammenligningTest.java` with table-driven cases for equal
  decimal scales, volume/cost increase/decrease, addition, removal, zero transition and product remap.
- [ ] T015 [P] [US1] Add property-style tests in the same class: for every key old + delta = new and
  aggregate product/type totals reconcile.
- [ ] T016 [P] [US1] Write `usage/BruksdataKandidatServiceIT.java`: archive/checksum, stored normalized
  rows, same-payload idempotency, invalid/incomplete refusal, candidate never becomes VALIDERT.
- [ ] T017 [P] [US1] Write `web/BruksdataSammenligningWebIT.java`: routes, role checks, 100-row paging,
  filters, no-change state, accessible labels and closed-period conditional routing.

### Implementation

- [ ] T018 [US1] Implement `usage/EffektivtBruksgrunnlagService.java` to derive the effective map and
  provenance from V1 plus EKSPORTERT correction selections; initially it returns V1 only.
- [ ] T019 [US1] Implement pure diff/classification in `usage/BruksdataDifferanse.java`, using
  `BigDecimal.compareTo` and explicit zero for absent sides.
- [ ] T020 [US1] Implement `usage/BruksdataKandidatService.java`: validate complete DWH input, archive
  raw bytes, persist KANDIDAT + normalized rows, identify latest exported baseline and create/reuse diff.
- [ ] T021 [US1] Add comparison persistence, exact summary/reconciliation and supersession rules in
  `usage/BruksdataSammenligningService.java`.
- [ ] T022 [US1] Change `usage/DwhImportService.java` so post-export fetch/file input delegates to the
  candidate service while pre-export staging/import semantics remain unchanged.
- [ ] T023 [US1] Route closed-period DWH API and recognized DWH-file submissions in
  `web/BruksdataController.java`; refuse native forsystem CSV as a correction candidate.
- [ ] T024 [US1] Add `web/BruksdataSammenligningController.java` GET with server-side filters/paging
  and baseline/candidate provenance.
- [ ] T025 [US1] Build `templates/bruksdata/sammenligning.html` per the UI contract: summary,
  reconciliation, old/new/difference, unchecked boxes, filters and no-change/superseded states.
- [ ] T026 [US1] Add candidate/comparison links and statuses to `templates/bruksdata/liste.html` and
  `templates/bruksdata/detalj.html` without relabelling ordinary imports.
- [ ] T027 [US1] Audit candidate creation, checksum, baseline, comparison creation/reuse and
  supersession by extending `common/Handling.java` and using `AuditService`.

**Checkpoint**: US1 is deployable behind no new export behavior; operators can trust and inspect diffs.

---

## Phase 3: User Story 2 — Select changes and generate V2 (P1)

**Goal**: A selected subset becomes a signed, versioned correction draft and nothing else.

**Independent Test**: Select increase + removal from four changes; V2 has exactly those two sources
and lines, uses V1 price/snapshots, and V1 remains immutable.

### Tests

- [ ] T028 [P] [US2] Write `billing/KorrigeringskjoringIT.java`: selection validation, required reason,
  exact selected subset, signed volume/cost lines, V1 price, V1 snapshot reuse and new-key controls.
- [ ] T029 [P] [US2] Extend `billing/KjoringVersjonIT.java`: V1→V2 linkage, monotonic allocation,
  discarded version not reused, per-period concurrent generation protection.
- [ ] T030 [P] [US2] Write `billing/KorrigeringsavstemmingIT.java`: every selection is invoiced or
  structurally blocked; signed basis/amount totals reconcile.
- [ ] T031 [P] [US2] Extend `web/BruksdataSammenligningWebIT.java`: unchecked default, select submit,
  invalid IDs, missing reason, stale baseline, CSRF and FORVALTER authorization.
- [ ] T032 [P] [US2] Add ordinary-run regression tests proving normal generation, approval, run
  uniqueness and ordinary line provenance are unchanged.

### Implementation

- [ ] T033 [US2] Extract reusable customer/product/price/control lookup helpers from
  `billing/FakturakjoringService.java` without changing the ordinary `generer(Periode)` result.
- [ ] T034 [US2] Implement `billing/KjoringVersjonService.java`: per-period DB/advisory lock, latest
  exported version lookup, next never-used version and stale-baseline validation.
- [ ] T035 [US2] Implement `billing/KorrigeringskjoringService.java` transaction: validate selection,
  persist run + selection links, use root price version/snapshots, create signed lines and findings.
- [ ] T036 [US2] Update `billing/FakturakjoringService.java` state transitions to carry version metadata
  unchanged and allow exported predecessor + one in-flight correction.
- [ ] T037 [US2] Implement `billing/KorrigeringsavstemmingService.java` and view model; leave
  `BruksdataavstemmingService` behavior unchanged for ordinary runs.
- [ ] T038 [US2] Add POST generate action to `web/BruksdataSammenligningController.java`, binding
  explicit IDs + reason + baseline token and redirecting to the new run.
- [ ] T039 [US2] Update run shared header/overview/control templates to show `Korrigering V2`, previous
  version, reason, selected signed totals, comparison link and correction reconciliation.
- [ ] T040 [US2] Ensure approve/discard actions and audit details include period/version/type; a
  discarded correction applies no change to the effective basis.

**Checkpoint**: V2 drafts can be generated, controlled, approved and discarded. Export remains gated.

---

## Phase 4: User Story 3 — Continue with V3 and disposition (P2)

**Goal**: Exported selections update the derived baseline; skipped changes remain actionable.

**Independent Test**: Exported V2 removes only selected deltas from a recomparison; an unselected
change remains and becomes V3.

### Tests

- [ ] T041 [P] [US3] Extend `usage/BruksdataSammenligningTest.java` for V1 + partial exported V2,
  same-candidate recomparison, removal/re-addition, multiple corrections and discarded-run exclusion.
- [ ] T042 [P] [US3] Add concurrency IT: two open browser selections race; exactly one run is created,
  the other receives the stale-baseline message and no orphan selection rows exist.
- [ ] T043 [P] [US3] Add dismissal/supersession web and service tests: reason required, history retained,
  newer candidate supersedes old OPEN comparison, dismissed difference may reappear in newer snapshot.

### Implementation

- [ ] T044 [US3] Complete `EffektivtBruksgrunnlagService` to apply selections from EKSPORTERT
  correction runs in version order and retain full provenance per effective key.
- [ ] T045 [US3] Add recompare action that reuses retained candidate against latest exported version;
  unique `(candidate, baseline)` makes retries idempotent.
- [ ] T046 [US3] Implement explicit dismissal in `BruksdataSammenligningService` and controller with
  actor/time/reason and audit; unchecked alone never dismisses.
- [ ] T047 [US3] Mark candidate BEHANDLET and comparison KJORING_OPPRETTET when a correction is created;
  allow reuse after export via a new comparison rather than mutating the old diff.
- [ ] T048 [US3] Add UI status/help for pending, dismissed, superseded and stale changes; provide
  `Sammenlign igjen` only when state permits.

**Checkpoint**: V1→V2→V3 produces a deterministic effective basis with no lost skipped changes.

---

## Phase 5: User Story 4 — Audit and version identity (P2)

**Goal**: Every surface distinguishes versions and exposes the full chain.

- [ ] T049 [P] [US4] Write `web/KjoringVersjonWebIT.java` for run-list order/labels, shared tab header,
  previous/candidate links, discarded attempt and history events.
- [ ] T050 [P] [US4] Add repository/service trace query test from correction line → change → old/new
  provenance → candidate raw checksum → previous run.
- [ ] T051 [US4] Update `web/FakturakjoringController.java` and run list/header/history view models for
  type, version, change count, signed net total and chain links.
- [ ] T052 [US4] Update `templates/kjoring/liste.html` and shared tab fragments to label V1/V2+ and
  order same-period versions newest first.
- [ ] T053 [US4] Add version/change provenance columns for correction spreadsheets in
  `export/FakturagrunnlagRegneark.java`; leave ordinary column values/output unchanged unless explicitly
  versioning a new download filename.
- [ ] T054 [US4] Add audit entries and entity links for compare, select, dismiss, generate, approve,
  discard, export and download; verify Historikk shows actor/time/reason.

---

## Phase 6: Confirmed correction export (blocked by T001/T002)

**Goal**: Deliver signed correction artifacts using only the accounting-approved mechanism.

- [ ] T055 [P] Write failing signed artifact tests in `export/KorrigeringEksportIT.java`: positive,
  negative, removal, net-zero run, net-negative invoice, exact signed CSV/XLSX/PDF totals and filenames.
- [ ] T056 [P] Add correction-specific LG04 writer/reader/golden tests in
  `export/Lg04KorrigeringTest.java` according to the recorded OQ-6/OQ-23 decision; do not alter the
  ordinary golden fixture.
- [ ] T057 Implement the confirmed Unit4 correction representation in `export/EksportService.java`
  and/or a dedicated adapter, guarded by `forsystem.korrigering.eksport-enabled`.
- [ ] T058 Make the confirmed batch identity unique per run version and version every correction
  filename/archive name; verify length/encoding constraints.
- [ ] T059 Extend pre-archive and archived-file reconciliation to signed correction totals and exact
  order counts; any mismatch leaves no files and keeps the run GODKJENT.
- [ ] T060 Execute `quickstart.md`'s tt02 accounting gate with VIS; archive test evidence and record
  date/result in `docs/07-open-questions.md` and `docs/09-beslutningslogg.md`.
- [ ] T061 Enable correction export in production configuration only after T060 passes; keep local
  and tt02 configuration explicit.

**Checkpoint**: OQ-6 is answered with evidence and correction export is safe to enable.

---

## Phase 7: Documentation, load and final regression

- [ ] T062 [P] Update `docs/03-datamodel.md` with V12 and the effective-basis invariant.
- [ ] T063 [P] Update `docs/brukerveiledning.md` with pre-export replacement versus post-export V2+
  correction, selection, dismissal, approval and audit walkthrough.
- [ ] T064 [P] Update `docs/runbook.md` with correction operations, stale/superseded handling and the
  prohibition on production reset.
- [ ] T065 [P] Update `docs/funksjonsoversikt.md`, `docs/08-traceability.md`,
  `docs/09-beslutningslogg.md` and `docs/10-presentasjon.md` with stable feature IDs and decisions.
- [ ] T066 Add a 10 000-key load fixture/test and verify comparison <10 s plus 100-row paging without
  storing the diff in HTTP session.
- [ ] T067 Run `./mvnw verify` with no external services; capture a before/after hash of the ordinary
  LG04 golden output and verify it is byte-identical.
- [ ] T068 Run the full manual local walkthrough in `quickstart.md`, including V2 discard→V3 and
  same-candidate recomparison, and attach findings to this task file.

---

## Dependencies & execution order

- T006–T013 (foundation) block all user stories.
- US1 (T014–T027) is the first independently demonstrable slice.
- US2 depends on US1 and enables correction drafts, but not export.
- US3 depends on US2's structural selection/run chain.
- US4 can overlap late US3 once run metadata and trace queries exist.
- Phase 6 is hard-blocked by T001/T002 and must pass T060 before T061.
- Documentation/load/final regression follows all delivered phases.

## Implementation strategy

1. Ship comparison-only behind the existing DWH feature configuration if early feedback is useful.
2. Add correction draft/approval with export visibly disabled.
3. Validate repeat V3 semantics and audit with users.
4. Implement and enable export only from recorded OQ-6/OQ-23 decisions and tt02 evidence.

Each phase ends with tests green and one conventional commit per logical change, per the constitution.

