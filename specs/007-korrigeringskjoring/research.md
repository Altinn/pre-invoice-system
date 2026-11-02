# Research: Versioned correction runs

Code and documentation findings as of 2026-11-02. Decisions below define the proposed architecture;
the two accounting/export gates remain owned by OQ-6/OQ-23.

## Findings

### R1 — Historical source rows are already retained

Since V8, re-import marks the old `bruksdata_import` AVVIST but keeps its `bruksdata` rows. Each
`fakturakjoring` records `bruksdata_import_id`. This is the right foundation: V1's source does not
need to be reconstructed from log text.

### R2 — Post-export re-import is deliberately blocked

`UsageImportService.vurder` blocks any new import while a non-FORKASTET run exists, and
`FakturakjoringService.forkast` refuses EKSPORTERT. The partial unique index also allows only one
non-FORKASTET run per period. These rules correctly protect V1 but cannot represent a V2 correction.

### R3 — The test reset is destructive and intentionally non-production

`NullstillingService` deletes a period's runs, invoices, findings, exports and imports. B-15 allows
this only behind `forsystem.testverktoy.enabled`; using it for corrections would violate traceability.

### R4 — Current imports represent full non-negative source state

DWH aggregation and the shared validator reject negative quantities/amounts. That remains correct
for source snapshots. Negative numbers arise only as derived differences and must not weaken source
validation.

### R5 — The generator can calculate negative amounts, but has no correction source

Generation multiplies `antall × enhetspris` without a non-negative check, and the invoice schema has
no positive-only constraint. However, lines point to one `bruksdata` row and cannot explain both old
and new values or a removal. A first-class change record is needed.

### R6 — The LG04 mechanics can carry a minus sign, but accounting semantics are unknown

`Lg04Skriver` places `belopOre` verbatim and `Lg04Leser` parses signed amounts; tests already include
a negative amount. This proves byte mechanics, not that VIS/Unit4 accepts the current transaction
type as a correction. OQ-6 explicitly says correction/crediting is undefined. OQ-23 also leaves the
final FinMod LG04 quantity/unit shape open.

### R7 — Export identity is not version-safe

Archive paths are isolated by run ID, but filenames contain only the period. `batchId` is the run's
generation date (`yyyyMMdd`), so two versions generated on the same day collide semantically even
if their files live in different directories. User-visible names and the confirmed Unit4 batch ID
must include a run version.

### R8 — Existing reconciliation assumes one full import per run

`BruksdataavstemmingService` reconciles a run against `fakturakjoring.bruksdata_import_id`. A
correction must instead reconcile selected change records to correction lines. V1 reconciliation
must remain unchanged.

### R9 — DWH staging recomputes normalization from raw data

A scheduled `MOTTATT` import currently stores raw payload only and rebuilds its preview on open.
That is useful before first import, but a correction comparison must be stable. Product mappings can
change later, so candidate normalized rows and the resulting diff must be persisted once created.

### R10 — Scale favors database-backed comparisons and paging

Current production expectations are a few hundred invoices, but DWH payloads can contain thousands
of normalized keys and daily raw rows. Persisted, indexed change rows support paging, filtering,
idempotency and audit without putting the full comparison in the HTTP session.

## Decisions

### D1 — Keep the pre-export replacement flow; add a separate post-export correction flow

- If the latest run has not been exported, behavior stays: discard, replace import, regenerate.
- If the period has an exported run, a new complete DWH snapshot becomes a correction candidate.
- Candidate creation never marks V1's import AVVIST and never changes `gjeldendeImport`.

This prevents a correction feature from weakening ordinary-run safety.

### D2 — Compare against effective invoiced state, not merely the latest file

The effective state is:

1. V1's immutable normalized import;
2. apply, in version order, selected changes from EKSPORTERT correction runs only;
3. ignore generated/approved/discarded corrections.

For a key, applying a correction sets it to that change's `ny_*` value; a removal removes it. This
lets unselected V2 changes remain visible for V3. The state can be recomputed from structural history;
no mutable "current billed row" table is required initially.

### D3 — Persist candidate snapshots, comparisons and change rows

Use `bruksdata_import` for the candidate payload and normalized full snapshot, with new status
`KANDIDAT`. Add a comparison parent and one child per changed key. Old/new/delta values are frozen
on the child even though source-row FKs are also retained. This gives stable review pages and makes
stale-baseline checks cheap.

The candidate does not become VALIDERT. `gjeldendeImport` continues to mean the ordinary source used
by V1.

### D4 — Selection belongs to the correction run

Checkboxes are not mutable flags on change rows. On generate, selected IDs are copied into a join
table `(kjoring_id, endring_id)`. The transaction:

1. locks the comparison;
2. verifies it is OPEN and its baseline run is still the latest exported version;
3. validates every selected ID belongs to it and is not dismissed;
4. allocates the next version;
5. creates run, selection links, invoices, lines and audit event atomically.

This prevents two browser tabs from double-applying a change.

### D5 — Version runs, do not version the month itself

Add `type` (`ORDINAER`, `KORRIGERING`), `versjon`, `forrige_kjoring_id`,
`sammenligning_id` and `korrigeringsaarsak` to `fakturakjoring`.

- Existing runs backfill to ORDINAER/V1 per period.
- The first ordinary attempt is V1. An ordinary retry after a pre-export discard receives the next
  unused version but remains type ORDINAER. Once any version is exported, later attempts are
  KORRIGERING only. A version number is never reused.
- Correction versions increment the maximum historical version for the period, including discarded
  attempts.
- One in-flight (GENERERT/GODKJENT) run per period is allowed; earlier EKSPORTERT versions coexist.

### D6 — Signed deltas are a domain fact; source snapshots remain non-negative

For BRUKSVOLUM, `delta_antall = new_antall - old_antall` and the line amount is
`delta_antall × V1 price`, rounded by the existing rule. For cost types,
`delta_belop = new_belop - old_belop` is the line amount. No synthetic negative DWH row is created.

`fakturalinje.bruksdata_endring_id` points to the selected change. Ordinary lines continue using
`bruksdata_id`; a check constraint enforces exactly the correct source for the run type at service
level and, where practical, in SQL.

### D7 — Reuse V1 commercial snapshots where they exist

The correction is for usage, not for price or customer-master changes.

- Use V1's `prisversjon_id` for every version in the chain.
- For a decrease/removal or change to an existing billed key, copy kundenummer, servicekode,
  references and recipient from the V1/root line and invoice snapshots.
- For a newly added key with no old line, resolve through the current registry and run the normal
  blocking controls. The created correction snapshots are then immutable.
- Do not silently fold registry or price differences into the usage diff.

### D8 — Preserve the ordinary state machine and approval controls

A correction is GENERERT → GODKJENT → EKSPORTERT or FORKASTET. Existing role checks and approval
blocking apply. An exported predecessor is never reopened. Discarding V2 leaves V1 and the effective
basis unchanged; a later attempt receives V3.

### D9 — Separate correction reconciliation

For a correction run, reconciliation lists every selected change and proves:

- exactly one selection link per included change;
- one correction line per selected change that passed resolution;
- signed basis and signed line amounts agree;
- any omitted selected change is structurally linked to a blocking finding.

The ordinary V1 reconciliation stays byte-for-byte behaviorally unchanged.

### D10 — Treat comparison freshness and payload identity explicitly

- Unique candidate identity: `(periode, raadata_sha256)`.
- Unique comparison identity: `(kandidat_import_id, grunnlag_kjoring_id)`.
- One OPEN comparison per period; a newer candidate marks the previous OPEN comparison SUPERSEDED.
- Generation checks `grunnlag_kjoring_id` against the latest exported version under a DB lock.
- Reusing the same candidate after V2 creates/reuses a new comparison against V2, which exposes only
  remaining differences.

### D11 — Make version identity visible everywhere

Use labels such as `Kjøring januar 2027 · V1` and `Korrigering januar 2027 · V2`. File names use
`<kind>-2027-01-v2.<ext>`. Archive directories remain per run ID. The batch ID must be unique per run,
but its exact externally visible format is held behind DG-2.

### D12 — Paginate and filter server-side

The comparison page defaults to 100 rows and supports query parameters for organisation, product,
change type, sign and page. Selection submission uses explicit change IDs plus a comparison ID and
baseline token; it never means "all rows matching the current filter" unless the user explicitly
uses a server-side select-all action with a shown count.

## Decision gates before implementation can complete

### DG-1 — OQ-6: Unit4 correction/credit mechanism

Owner: Økonomi + VIS.

Need a recorded answer for:

- whether a negative amount in today's sales-order LG04 is accepted;
- whether corrections require a credit note, different transaction/order/document type, or a
  separate positive/negative delivery;
- treatment of a net-negative invoice and a full removal;
- whether the correction must reference the original Unit4 invoice/order;
- operational approval and email wording.

Acceptance evidence: representative V2 file imported successfully in tt02 and Unit4 report totals
match exactly. Until then, comparison and draft generation may be developed, but production export
of negative correction runs stays disabled.

### DG-2 — OQ-6/OQ-23: unique batch/version format

Owner: FEL + VIS regnskap.

Need the accepted character set, maximum length and uniqueness expectation for `batchId`, plus the
required version/file naming convention. Do not assume `yyyyMMdd-v2` is accepted merely because it
fits the writer field.

### DG-3 — Product decision: dismissal semantics

Proposed default: unchecked changes remain pending; explicit dismissal requires a reason and only
affects that candidate comparison. A later newer DWH snapshot may surface the difference again if it
still differs from the effective billed basis. Confirm this wording with the operational users during
spec review; it does not block schema design.
