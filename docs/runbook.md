# Runbook — forsystem

Operational guide for the monthly invoicing pre-run, plus how to demo the system locally. This is
the MVP process: LG04 and PDF are **downloaded from the UI and sent onward by a person** (docs/01),
mirroring how FEL operates the old system today.

The user-facing steps of this procedure are taught in the app under **Hjelp** (`/hjelp`, section
Månedsrutinen; sources in `src/main/resources/hjelp/`). Keep the two in step.

Roles (from OIDC groups; stubbed to a dev user with all roles under the `local` profile):
`LESER` reads, `FORVALTER` maintains registries + imports + generates, `GODKJENNER` approves +
exports.

---

## Monthly procedure (invoicing the previous month)

The billing period is always chosen explicitly (the first day of the measured month) — never
derived from today's date.

1. **Registries up to date** (FORVALTER). Under *Produkter* and *Prisversjoner*:
   - the year's price version is `AKTIV` and covers the period;
   - every product that will be billed has kontering (konto + dimensjoner + artikkel-id) — until
     Økonomi delivers these (OQ-2), generation will block with `MANGLER_KONTERING`.
   There is no customer step: customer data (name, kundenummer, fakturareferanse, bestillingsnummer)
   is maintained in CRM and arrives with the DWH usage (specs/018).
2. **Import usage** (FORVALTER) under *Bruksdata*, from the 7th (the DWH basis is complete on the
   6th): pick the period and **Hent fra datavarehus** — or, if the scheduler is on, open the `MOTTATT`
   import it staged. Fallback: upload the CSV (format in [csv-format.md](csv-format.md)). Review the
   staging report (per-product sums, warnings, any rejects, and *Kundedata*: organisations without
   kundenummer or with conflicting customer values — have them corrected in CRM before the run, they
   block generation), then **Bekreft import**. An unknown DWH
   product name must be mapped under *Produkter → Kildenavn* first. A file with any reject imports nothing —
   fix it and re-upload. Re-importing a period supersedes it: the previous import becomes `AVVIST`
   and its rows are kept for traceability.
   Uploading the DWH's flagged export (`flag_inv_altinn`; limit 100 MB): the preview first shows *Innholdet
   i fila* — used / other months / DPV / excluded, with a control sum equal to the file's row count. Only
   rows flagged for Altinn invoicing in the chosen period are imported, so upload the same file once per
   month. DPV rows are counted but not billed here (OQ-26). After confirming, each usage row on the
   import's page expands to its source groups (table, resource, day rows, `regel`). See
   [csv-format.md](csv-format.md).
3. **Generate** (FORVALTER) under *Kjøringer*: choose the period and **Generer**. Review the run:
   the invoice table and the control findings.
   - **BLOKKERENDE** findings must be resolved before approval (fix the data, **Forkast** the run,
     regenerate). `MANGLER_KUNDENUMMER` and `MOTSTRIDENDE_KUNDEDATA` are fixed in CRM, then the usage
     is imported again once the DWH has the correction.
   - **ADVARSEL** findings (±30 % deviation vs the previous period, zero-amount lines, new
     customers) are informational — check them, then proceed.
   - Control before approval (BF1 T01/T06/T10): the *Sammendrag* on the run page, **Avstemming
     bruksdata** (imported vs invoiced, and every row kept off the invoice with its reason), and
     **Last ned fakturagrunnlag (XLSX)** to sample in Excel. None of these change the run.
4. **Approve** (GODKJENNER): **Godkjenn** the run once there are no blocking findings.
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
(products/prices in forsystem; customer data in CRM, then re-import usage), then **Generer** again. The discarded run keeps its own usage
import and stays explainable (its *Avstemming bruksdata* still works).

**Test environments only (local, tt02): Nullstill periode.** To repeat a month end to end — including
after export — use *Testverktøy: nullstill periode* at the bottom of the Kjøringer page (FORVALTER).
It permanently deletes the month's runs, invoices, findings, LG04 reconciliation, export files and
usage imports, plus their archived files; registries and hendelseslogg stay, and the reset is logged
as `NULLSTILTE`. It exists only when `FORSYSTEM_TESTVERKTOY_ENABLED=true`, which only the tt02
overlay (`syncroot/tt02`) and the `local` profile set. Never set it in prod.

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

- **Unknown kundenummer / account** in Unit4 → have the kundenummer corrected in CRM (and re-import
  usage once the DWH carries it), or correct the product kontering; then forkast + regenerate +
  re-export the period.
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
(DEMO)*, and an imported January-2027 usage batch for six organisations whose customer values come in
the CSV's optional customer columns (one without fakturareferanse, one with a bestillingsnummer, two
sharing a kundenummer). There is no customer registry to browse.

Demo walkthrough:

1. Open http://localhost:8080 — you are logged in as the dev user (all roles).
2. Browse *Produkter* and *Prisversjoner* to show the registries.
3. *Bruksdata* shows the pre-imported January batch. (To show the import flow live, download
   `src/main/resources/demo/bruk-2027-01.csv`, delete the batch is not needed — just re-upload it
   for a new period, or upload for another month. Its preview shows the *Kundedata* section.)
4. *Kjøringer* → **Generer** period `2027-01` → open the run: one invoice per organisation with the
   customer values from the file, the shared Politi kundenummer, and control findings.
5. **Godkjenn**, then **Eksporter**. Download the LG04, the PDF zip, and the CSV/XLSX.
6. *Hendelseslogg* shows every step audited.

`docker compose up` always yields this complete flow offline — no Azure, no external identity
provider, files archived to a local directory.

---

## Upgrading to V18 (specs/018 — customer registry dropped)

Flyway V18 drops the tables `kunde`, `kunde_referanse` and `kundenummer_regel` (V17, which runs
just before it, copies what invoices need onto `faktura` and `bruksdata_kunde`). Their
change history stays in hendelseslogg, but the rows themselves are gone after V18. **Before deploying
V18 to an environment that has registry data (tt02, prod), dump the three tables** and keep the file
with the deployment record:

```bash
pg_dump --data-only -t kunde -t kunde_referanse -t kundenummer_regel "$DATABASE_URL" > kunderegister-før-018.sql
```

The dump is also the data to check OQ-28 against (customers with several kundenummer, product-specific
references or a deviating fakturamottaker).

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
