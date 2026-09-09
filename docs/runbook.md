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
2. **Import usage** (FORVALTER) under *Bruksdata*: pick the period, upload the CSV
   (format in [csv-format.md](csv-format.md)), review the staging report (per-product sums and any
   rejects with line numbers), then **Bekreft import**. A file with any reject imports nothing —
   fix it and re-upload. Re-importing a period replaces it and marks the previous import `AVVIST`.
3. **Generate** (FORVALTER) under *Kjøringer*: choose the period and **Generer**. Review the run:
   the invoice table and the control findings.
   - **BLOKKERENDE** findings must be resolved before approval (fix the data, **Forkast** the run,
     regenerate).
   - **ADVARSEL** findings (±30 % deviation vs the previous period, zero-amount lines, new
     customers) are informational — check them, then proceed.
4. **Approve** (GODKJENNER): **Godkjenn** the run once there are no blocking findings.
5. **Export** (GODKJENNER): **Eksporter**. Four files are produced and archived (with SHA-256):
   `LG04` (the Unit4 import file), `PDF` (a zip of per-invoice detail views), and `CSV` + `XLSX`
   of the lines. Download them from the run page.
6. **Send to DFØ** (manual): send the downloaded LG04 file to DFØ through the agreed channel for
   import into Unit4. The file is already Windows-1252 encoded — **no `iconv` step**. Distribute the
   PDF detail views to tjenesteeiere as agreed.

## Re-run a period

A period may only have one active run. To redo one: open the run, **Forkast** it, fix the data
(registries and/or re-import usage), then **Generer** again. Forkasting is blocked once a run is
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

When an environment is available: build the image (CI already publishes it), deploy to Container
Apps or AKS with the `prod` profile, point `DB_*` at the managed PostgreSQL, set the OIDC issuer,
and add the Blob adapter. Nothing in the core changes.
