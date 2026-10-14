# Runbook — forsystem

Operational guide for the monthly invoicing pre-run, plus how to demo the system locally. This is
the MVP process: LG04 and PDF are **downloaded from the UI and sent onward by a person** (docs/01),
mirroring how FEL operates the old system today.

Roles (from OIDC groups; stubbed to a dev user with all roles under the `local` profile):
`LESER` reads, `FORVALTER` maintains registries + imports + generates, `GODKJENNER` approves +
exports.

---

## Monthly procedure (invoicing the previous month)

The billing period is always chosen explicitly (the first day of the measured month) — never
derived from today's date.

1. **Registries up to date** (FORVALTER). Under *Produkter*, *Prisversjoner*, *Kunder*:
   - the year's price version is `AKTIV` and covers the period;
   - every product that will be billed has kontering (konto + dimensjoner + artikkel-id) — until
     Økonomi delivers these (OQ-2), generation will block with `MANGLER_KONTERING`;
   - customers, references and kundenummer-rules reflect current agreements.
2. **Import usage** (FORVALTER) under *Bruksdata*, from the 7th (the DWH basis is complete on the
   6th): pick the period and **Hent fra datavarehus** — or, if the scheduler is on, open the `MOTTATT`
   import it staged. Fallback: upload the CSV (format in [csv-format.md](csv-format.md)). Review the
   staging report (per-product sums, warnings, any rejects), then **Bekreft import**. An unknown DWH
   product name must be mapped under *Produkter → Kildenavn* first. A file with any reject imports nothing —
   fix it and re-upload. Re-importing a period supersedes it: the previous import becomes `AVVIST`
   and its rows are kept for traceability.
3. **Generate** (FORVALTER) under *Kjøringer*: choose the period and **Generer**. Review the run:
   the invoice table and the control findings.
   - **BLOKKERENDE** findings must be resolved before approval (fix the data, **Forkast** the run,
     regenerate).
   - **ADVARSEL** findings (±30 % deviation vs the previous period, zero-amount lines, new
     customers) are informational — check them, then proceed.
   - Control before approval (BF1 T01/T06/T10): the *Sammendrag* on the run page, **Avstemming
     bruksdata** (imported vs invoiced, and every row kept off the invoice with its reason), and
     **Last ned fakturagrunnlag (XLSX)** to sample in Excel. None of these change the run.
4. **Approve** (GODKJENNER, a *different* person from the one who generated the run — four-eyes,
   Økonomi 2026-10-13): **Godkjenn** the run once there are no blocking findings. Every environment
   therefore needs at least two people with the GODKJENNER role. The rule is on by default
   (`FORSYSTEM_SIDEMANNSKONTROLL=true`); only the offline `local` profile turns it off.
5. **Export** (GODKJENNER): **Eksporter**. The LG04 is read back and reconciled against the run
   before anything is saved; the run page then shows **LG04 stemmer med fakturagrunnlaget** with the
   file's and the run's orders and amount (BF1 T15). Four files are produced and archived (with
   SHA-256): `LG04` (the Unit4 import file), `PDF` (a zip of per-invoice detail views), and `CSV` +
   `XLSX` of the lines. Download them from the run page. If the export is stopped instead, see
   [Eksport stoppet](#eksport-stoppet-lg04-does-not-match-the-run).
6. **Send to DFØ** (manual): send the downloaded LG04 file to DFØ through the agreed channel for
   import into Unit4. Copy the figures for the covering email (periode, antall fakturaer, samlet
   beløp) from *Til e-post til regnskap* on the run page — VAT treatment is still open (OQ-11). The file is already Windows-1252 encoded — **no `iconv` step**. Distribute the
   PDF detail views to tjenesteeiere as agreed.

## Re-run a period

A period may only have one active run. To redo one: open the run, **Forkast** it, fix the data
(registries and/or re-import usage), then **Generer** again. The discarded run keeps its own usage
import and stays explainable (its *Avstemming bruksdata* still works).

## Eksport stoppet (LG04 does not match the run)

The export reads the LG04 file back before saving anything. If its order count or amount differs
from the run, the export is **stopped**: no files are written, the run stays `GODKJENT`, an
`EKSPORT_STOPPET` event with both sets of figures is logged, and the run page explains it. The file
is built from the run's own lines, so this is always a **defect in forsystem**, never a data error
the operator can fix. Nothing needs undoing: contact the forsystem team with the run id and the
message, and once the defect is fixed, press **Eksporter** again on the same run.

**Kontroller arkivert LG04 på nytt** (run page, any exported run) re-reads and re-hashes the
archived file without changing anything — use it if anyone doubts that the delivered file is the
reconciled one. Forkasting is blocked once a run is
`EKSPORTERT` — a new period/correction mechanism (avregning) is future scope (OQ-6).

## On a DFØ / Unit4 import-error email

Errors come back to humans by email (no machine ingestion in the MVP). Typical causes and fixes:

- **Unknown kundenummer / account** in Unit4 → correct the `kundenummer_regel` or product kontering,
  then forkast + regenerate + re-export the period.
- **File rejected on format** → re-download and resend; if it recurs, capture the exact bytes and
  compare against the golden format (the LG04 layout is locked by `Lg04SkriverTest`). A deliberate
  format change must be a conscious golden-file update.
- Keep the run in `EKSPORTERT`; issue a corrected run under the future avregning mechanism when
  defined. For the dry-run, coordinate directly with the FEL contact (FEL).

---

## Demo it locally (no Azure needed)

```bash
docker compose up --build
```

Brings up the app (`:8080`) + PostgreSQL, and on first start the `demo` profile seeds a realistic,
clearly-fake dataset (see `Demodata`): products with demo kontering, an active *Prisliste 2027
(DEMO)*, six customers covering every special case (deviating recipient, product-specific reference,
two kundenummer, shared kundenummer), and an imported January-2027 usage batch.

Demo walkthrough:

1. Open http://localhost:8080 — you are logged in as the dev user (all roles).
2. Browse *Produkter*, *Prisversjoner*, *Kunder* to show the registries and the special cases.
3. *Bruksdata* shows the pre-imported January batch. (To show the import flow live, download
   `src/main/resources/demo/bruk-2027-01.csv`, delete the batch is not needed — just re-upload it
   for a new period, or upload for another month.)
4. *Kjøringer* → **Generer** period `2027-01` → open the run: invoices grouped by kundenummer, the
   deviating recipient, two Utdanningsdirektoratet invoices, the shared Politi kundenummer, and
   control findings.
5. **Godkjenn**, then **Eksporter**. Download the LG04, the PDF zip, and the CSV/XLSX.
6. *Hendelseslogg* shows every step audited.

`docker compose up` always yields this complete flow offline — no Azure, no external identity
provider, files archived to a local directory.

---

## Naming the product dimensions

The product pages label Dim 1, Dim 2 and Dim 4 from configuration, empty by default because what each
dimension means is still open (OQ-2). Once Økonomi confirms the names, set them per environment —
no code change:

```
PRODUKT_DIM1_NAVN=<name>   # e.g. koststed, if Økonomi confirms it
PRODUKT_DIM2_NAVN=<name>
PRODUKT_DIM4_NAVN=<name>
```

## Deployment (Azure) — deferred

Azure provisioning is out of scope for the local MVP and is not yet available. The application is
built to be Azure-ready but not Azure-locked (docs/02):

- **Config** is 12-factor (environment variables / Spring profiles); no value requires Azure.
- **Auth** is OIDC — set `prod` profile with `OIDC_ISSUER_URI` / `OIDC_CLIENT_ID` /
  `OIDC_CLIENT_SECRET` (Entra ID is one issuer among any).
- **File archive** is behind the `FileArchive` port. The MVP uses `LocalFileArchive`; an
  `AzureBlobArchive` adapter is a **prepared seam, not yet implemented** — deliberately, to avoid
  shipping an untested Azure SDK dependency before an environment exists. It is a single new class
  in the `archive` package implementing `FileArchive`, selected by profile, with no change to the
  core.
- **Secrets** are plain environment variables; in Azure they are supplied by Key Vault references /
  managed identity at the platform level — the application never calls Key Vault.
- **Database** is vanilla PostgreSQL 16 (no Azure-specific extensions).
- **Datavarehus** (usage source, off by default): `FORSYSTEM_DWH_ENABLED=true`, `DWH_BASE_URL`
  (the Data API Builder host, without `/api`), `DWH_AUTH=none|entra` and, for `entra`, `DWH_SCOPE`
  — the token comes from the pod's workload identity (`AZURE_*` variables injected by AKS), no
  secret. `FORSYSTEM_DWH_PLANLEGGING=true` stages the previous month at 06:00 on the 7th. The pod
  needs egress to `*.azurecontainerapps.io`. **Enabled in tt02 and prod since 2026-10-07** (base
  deployment) for testing by the forvaltere. Until OQ-18 is closed, only confirm (Bekreft) a DWH
  import in prod once the Kildenavn mapping in that environment matches what the product owners
  have confirmed.

When an environment is available: build the image (CI already publishes it), deploy to Container
Apps or AKS with the `prod` profile, point `DB_*` at the managed PostgreSQL, set the OIDC issuer,
and add the Blob adapter. Nothing in the core changes.
