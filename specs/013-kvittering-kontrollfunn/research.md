# Research: Acknowledge control findings and export the Kontroller lists

## D1 — Store acceptance on the finding row, not in a side table

**Decision**: `kontrollfunn.akseptert_av text`, `akseptert_at timestamptz`, with
`check ((akseptert_av is null) = (akseptert_at is null))` and
`check (akseptert_at is null or alvorlighet = 'ADVARSEL')`.

**Rationale**: A finding is accepted or not; the current state is one fact per row. The history of
changes (accept, un-accept, accept again) lives in `hendelseslogg`, which is already the audit trail and
already shown per run on the Historikk tab. The second check makes FR-002 hold even against a defect.

**Alternatives**: A `kontrollfunn_aksept` table with one row per change — duplicates the audit trail, and
the current state then needs a "latest row" query everywhere the badge is counted.

## D2 — Per finding, with a group shortcut

**Decision**: The unit is the finding. The group row's checkbox posts `kode` and accepts/un-accepts every
ADVARSEL of that code in the run, logged as one history row listing the ids.

**Rationale**: Findings name one customer or invoice; the approval gate and the export need per-finding
state. But codes like `AKTIV_KUNDE_UTEN_BRUK` or `MANGLER_FAKTURAREFERANSE` can have dozens of rows in the
first months, and Økonomi reviews them as a list. A group action that is explicit («Funn akseptert» on the
row that says «30») and logged is not a hidden bulk default.

**Alternatives**: Group only — loses which customer was looked at, and a group that grows on regeneration
would be ambiguous. Finding only — 30 clicks for one reviewed list.

## D3 — Roles

**Decision**: `POST /kjoringer/*/kontrollfunn/**` → `hasAnyRole(FORVALTER, GODKJENNER)`, placed before the
catch-all FORVALTER rule. LESER is refused.

**Rationale**: `SecurityConfig` keeps roles non-hierarchical (FORVALTER maintains/generates, GODKJENNER
approves/exports). Acknowledging is review work both do: FEL (FORVALTER) investigates findings after
generation, Økonomi (GODKJENNER) signs off. Restricting it to GODKJENNER would force Økonomi to click
through findings FEL has already resolved; the name on each acceptance keeps it accountable, and approval
stays GODKJENNER-only.

## D4 — Locking and status

**Decision**: Both aksept methods load the run with `finnForOppdatering` (row lock), as `godkjenn` and
`endreTekster` do, and refuse unless GENERERT.

**Rationale**: Without the lock, an un-accept and an approval in parallel could both commit, leaving an
approved run with an unaccepted warning. With it they serialise, and `godkjenn` re-checks under the lock.

## D5 — UI: auto-submitting checkbox

**Decision**: Each checkbox sits in its own small POST form (`layout :: csrf`) with
`data-auto-send`; forsystem.js submits the form on `change` (`requestSubmit`), and htmx boost swaps the
page without jumping to the top (`hx-swap="innerHTML show:none"` on the form; checked in the browser).
A `<noscript>` «Lagre» button covers no-JS. The redirect lands on `/kjoringer/{id}/kontroller#kontrollfunn`.

**Rationale**: Økonomi asked for a checkbox, not a button; one request per change keeps each change a
separate audited act. The page already uses delegated listeners in forsystem.js.

## D6 — Counting

**Decision**: A small `Funnstatus` (web) computes blokkerende, åpne advarsler and aksepterte advarsler from
the run's findings, plus the Norwegian wording (`1 advarsel` / `2 advarsler`). The tab badge shows
blokkerende if any, else åpne. The run list shows blokkerende and åpne tags and a quiet «N sjekket ut».

## D7 — Exports

**Decision**: Three more list names in `EksportService.liste`:

- `kontrollfunn`: periode, alvorlighet, kode, beskrivelse, ordre, kundenummer, kundenavn, funn_akseptert
  (ja/nei, empty for blokkerende), akseptert_av, akseptert_tidspunkt (ISO local time Europe/Oslo).
  Ordered as on the tab (blocking first, then code), then by id.
- `avstemming-bruksdata`: produkt, produktnavn, type, importerte_rader, importert_bruksdata,
  fakturerte_linjer, fakturert_bruksdata, ikke_fakturerte_rader.
- `ikke-fakturert`: organisasjonsnummer, produkt, type, antall, belop_eksl_mva, kode, arsak.

The two reconciliation lists are refused for correction runs (no reconciliation; the tab shows the
comparison link instead). `EksportService` gets `BruksdataavstemmingService` injected (same module
dependency as `FakturakjoringService`).

**PDF/image**: not built — the tab prints to PDF through the browser with the existing print stylesheet.
