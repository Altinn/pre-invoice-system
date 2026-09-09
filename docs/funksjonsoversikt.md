# Feature inventory — forsystem

A complete, stable-ID inventory of everything the forsystem MVP does, so it can be **cross-referenced
against the product owner's requirements**. Descriptions are in English; domain/UI terms are kept in
Norwegian exactly as they appear in the app (e.g. *fakturakjøring*, *kontrollfunn*, *prisversjon*).

**How to use this for mapping:** each feature has a stable ID (`F-<AREA>-<n>`). For every PO
requirement, find the feature(s) that satisfy it and record the ID. Anything in a requirement with no
matching feature is a gap; anything here with no matching requirement is either scope we added for
correctness or a candidate to confirm. The [Not built / deferred](#not-built--deferred) and
[Open questions](#open-questions-affecting-scope) sections are deliberately included so gaps are
explicit.

**Column legend:** *Where* = the UI page, HTTP endpoint, and/or the main class. *Role* = minimum role
required (— = read/none). *Ref* = source doc / open question.

Status of the whole MVP: **Phases 0–5 complete** (docs/04). All features below are implemented and
covered by automated tests (61 tests; byte-exact LG04 golden) unless the row says otherwise.

---

## Registry — Produkt (products)

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-REG-PROD-01 | List products | View the six Altinn products with kode, navn, enhet, kontering, aktiv. | GET `/produkter` · `ProduktController` | LESER | docs/03 V1 |
| F-REG-PROD-02 | Seeded product catalog | Six products preloaded: melding, formidling, varsling, autorisasjon, studio, appinfra. | Flyway `V6` | — | docs/03 V6 |
| F-REG-PROD-03 | Create product | Add a product (kode, navn, enhet). | POST `/produkter` | FORVALTER | docs/04 P1 |
| F-REG-PROD-04 | Edit product + kontering | Edit navn, enhet, aktiv, and kontering: artikkel_id, konto, dim_1/2/4. | GET/POST `/produkter/{id}` | FORVALTER | docs/04 P1 |
| F-REG-PROD-05 | Kontering nullable until OQ-2 | Kontering stays NULL until Økonomi delivers values; NULL is deliberately allowed here and blocked at generation. | `ProduktService` | — | OQ-2 |

## Registry — Prisversjon / Pris (price versions & prices)

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-REG-PRIS-01 | List price versions | All versions with navn, gyldig fra/til, status. | GET `/prisversjoner` | LESER | docs/03 V1 |
| F-REG-PRIS-02 | Create draft version | New prisversjon (UTKAST) with navn, gyldig_fra, optional gyldig_til. | POST `/prisversjoner` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-03 | Set unit price | Set/update enhetspris per product — only while UTKAST. | POST `/prisversjoner/{id}/pris` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-04 | Lifecycle UTKAST→AKTIV→ARKIVERT | One-way status transitions. | `PrisversjonService` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-05 | Activate with validation | Activation requires every *active* product to have a price. | POST `/prisversjoner/{id}/aktiver` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-06 | One active version per date range | Activation blocked if another AKTIV version overlaps the date range. | `PrisversjonService` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-07 | Archive active version | AKTIV → ARKIVERT. | POST `/prisversjoner/{id}/arkiver` | FORVALTER | docs/04 P1 |
| F-REG-PRIS-08 | Prices locked outside draft | enhetspris editable only in UTKAST. | `PrisversjonService` | — | docs/04 P1 |

## Registry — Kunde (customers) and the FinMod special cases

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-REG-KUN-01 | List customers | virksomhet, orgnr, fakturamottaker, avtalestatus. | GET `/kunder` | LESER | docs/03 V2 |
| F-REG-KUN-02 | Create customer | orgnr (9 digits, validated), navn, fakturamottaker, avtalestatus. | GET `/kunder/ny`, POST `/kunder` | FORVALTER | docs/04 P1 |
| F-REG-KUN-03 | Edit customer | Update navn, fakturamottaker, avtalestatus. | POST `/kunder/{id}` | FORVALTER | docs/04 P1 |
| F-REG-KUN-04 | Unique orgnr | Duplicate orgnr rejected (DB unique + service check). | `KundeService` / DB | — | docs/03 V2 |
| F-REG-KUN-05 | Deviating fakturamottaker | Invoice a different legal entity than the usage-generating orgnr. | `fakturamottaker_orgnr` | FORVALTER | docs/01 rule 1, OQ-7 |
| F-REG-KUN-06 | Fakturareferanser (list/add/delete) | References per customer. | GET `/kunder/{id}`, POST `/kunder/{id}/referanser`, POST `/referanser/{id}/slett` | FORVALTER | docs/03 V2 |
| F-REG-KUN-07 | Product-specific reference | A product-specific fakturareferanse/bestillingsnummer overrides the customer default. | `KundeReferanse` (produkt_id) | FORVALTER | docs/01 rule 2 |
| F-REG-KUN-08 | Kundenummerregler (list/add/delete) | Map customer → Unit4 kundenummer, optionally per produkt/servicekode. | POST `/kunder/{id}/regler`, POST `/regler/{id}/slett` | FORVALTER | docs/03 V2 |
| F-REG-KUN-09 | Multiple kundenummer per customer | Different servicekode/produkt → several kundenummer → several fakturaer. | `KundenummerRegel` | FORVALTER | docs/01 rule 3–4 |
| F-REG-KUN-10 | Shared kundenummer across customers | Same kundenummer for several orgs, distinguished by tilleggstekst. | `KundenummerRegel.tilleggstekst` | FORVALTER | docs/01 rule 3 |
| F-REG-KUN-11 | Temporary registry, CRM-ready | `kilde` column marks source; snapshots mean a future CRM switch never rewrites history. | `Kunde.kilde` | — | OQ-5, docs/06 §2 |
| F-REG-KUN-12 | BRREG validation | On create/update, validate against Brønnøysund (data.brreg.no) behind a port: an unknown orgnr **blocks** (prod); a name mismatch is a **non-blocking warning** (flash on the customer page); permissive when BRREG is unreachable or offline (demo/test). | `BrregOppslag` / `BrregHttpOppslag` (prod) / `PermissivBrregOppslag` (offline); `KundeService.opprett`/`brregNavnAdvarsel`, `KundeController` | FORVALTER | K-16, docs/01 rule 5 |

## Usage import — Bruksdata

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-USG-01 | CSV upload per period | Upload a period's usage as CSV. | GET `/bruksdata`, POST `/bruksdata/forhandsvis` · `CsvUploadUsageSource` | FORVALTER | docs/04 P2 |
| F-USG-02 | UsageDataSource port | Source behind a port so a datavarehus adapter can slot in later; same validation pipeline. | `UsageDataSource` | — | OQ-4, docs/06 §1 |
| F-USG-03 | Documented CSV contract | Column contract; SMS/Azure marked provisional. | `docs/csv-format.md` | — | OQ-3 |
| F-USG-04 | Staging report before commit | Per-product sums, valid/rejected counts, rejects with line numbers — shown before persisting. | POST `/bruksdata/forhandsvis` · `Forhaandsvisning` | FORVALTER | docs/04 P2 |
| F-USG-05 | Validation: wrong period | Row periode ≠ declared period → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-06 | Validation: malformed orgnr | Not 9 digits → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-07 | Validation: unknown product | produktkode not registered → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-08 | Validation: invalid type | type not BRUKSVOLUM/AZURE_KOSTNAD/SMS_KOSTNAD → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-09 | Validation: amount rules | Missing/non-numeric/negative antall (volume) or belop (cost) → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-10 | Validation: duplicates | Duplicate (periode, orgnr, produkt, type) → rejected. | `UsageImportService` | — | docs/04 P2 |
| F-USG-11 | All-or-nothing import | Any reject blocks the whole file; a rejected file leaves zero rows. | POST `/bruksdata/bekreft` | FORVALTER | docs/04 P2 |
| F-USG-12 | Re-import replacement | Re-importing a period deletes prior rows and marks the old import AVVIST. | `UsageImportService` | FORVALTER | docs/03 §4 |
| F-USG-13 | Block import on active run | Import blocked if a non-FORKASTET kjøring exists for the period. | `KjoringStatusPort` (billing adapter) | — | docs/04 P2 |
| F-USG-14 | Import detail view | View an import's rows. | GET `/bruksdata/{id}` | LESER | docs/04 P2 |
| F-USG-15 | Pass-through cost rows | AZURE_KOSTNAD and SMS_KOSTNAD carried as belop. | `Bruksdatatype` | — | OQ-3 |

## Generation engine — fakturagrunnlag

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-GEN-01 | Generate run for a period | For a period + the active prisversjon covering it, build fakturaer + fakturalinjer. | POST `/kjoringer` · `FakturakjoringService.generer` | FORVALTER | docs/04 P3 |
| F-GEN-02 | Resolve customer from usage orgnr | Each usage orgnr resolved to a kunde. | `FakturakjoringService` | — | docs/04 P3 |
| F-GEN-03 | Group by (kunde, kundenummer) | Invoices group by kundenummer, not by customer. | `FakturakjoringService` | — | docs/03 §2 |
| F-GEN-04 | Assign kundenummer by product | Product-specific kundenummerregel wins, else the customer default. | `FakturakjoringService.velgRegel` | — | OQ-10 |
| F-GEN-05 | BRUKSVOLUM lines | belop = antall × enhetspris, rounded half-up to 2 decimals. | `FakturakjoringService` | — | docs/04 P3 |
| F-GEN-06 | Pass-through cost lines | Azure/SMS cost lines from bruksdata.belop (no price/antall). | `FakturakjoringService` | — | docs/04 P3 |
| F-GEN-07 | Line description | e.g. «Bruk av melding januar 2027» (period label from the run parameter). | `FakturakjoringService` | — | docs/03 |
| F-GEN-08 | Snapshots at generation | kundenummer, tilleggstekst, fakturamottaker, fakturareferanse, bestillingsnummer, servicekode snapshotted so later edits don't rewrite history. | `Faktura`/`Fakturalinje` | — | docs/03 §1 |
| F-GEN-09 | Structural traceability | Each line links to its bruksdata_id and pris_id; run links to prisversjon. | `Fakturalinje` | — | docs/03 §1 |
| F-GEN-10 | Deterministic ordering | Invoices ordered by (orgnr, kundenummer); lines by (produkt kode, type); ordre_nr sequential. | `FakturakjoringService` | — | docs/05 |
| F-GEN-11 | ordre_nr on faktura | Order id per invoice; enables splitting into LG04 orders at export. | `Faktura.ordre_nr` | — | docs/03 §5, OQ-1 |
| F-GEN-12 | UUIDv7 faktura_uuid | Time-ordered UUID; also the PDF filename. | `common.UuidV7` | — | docs/03 |
| F-GEN-13 | Totals = exact sum of lines | faktura.sum_belop = exact sum of line belop. | `FakturakjoringService` | — | docs/04 P3 |
| F-GEN-14 | Run + faktura screens | Run overview (invoices + findings) and faktura drill-down to lines/bruksdata. | GET `/kjoringer/{id}`, `/kjoringer/{id}/faktura/{fid}` | LESER | docs/04 P3 |

## Controls — kontrollfunn

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-CTRL-01 | MANGLER_KUNDE (blocking) | Usage orgnr with no kunde. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-02 | MANGLER_KUNDENUMMER (blocking) | Customer without an applicable kundenummerregel. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-03 | INAKTIV_AVTALE (blocking) | Customer avtalestatus ≠ AKTIV. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-04 | MANGLER_PRIS (blocking) | Product without a price in the active version. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-05 | MANGLER_KONTERING (blocking) | Product with NULL kontering. | `FakturakjoringService` | — | OQ-2 |
| F-CTRL-06 | STORT_AVVIK (warning) | Customer sum deviates > ±30 % vs the previous period. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-07 | NULLBELOP (warning) | Invoice has a zero-amount line. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-08 | NY_KUNDE (warning) | Customer not invoiced in the previous period. | `FakturakjoringService` | — | docs/04 P3 |
| F-CTRL-09 | Findings surfaced in UI | Blocking + warnings shown on the run and per-faktura. | GET `/kjoringer/{id}` | LESER | docs/04 P3 |

## Approval & state machine — fakturakjøring

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-APPR-01 | Status GENERERT→GODKJENT→EKSPORTERT | Run lifecycle. | `FakturakjoringService` | — | docs/04 P3 |
| F-APPR-02 | Approve | Move GENERERT → GODKJENT. | POST `/kjoringer/{id}/godkjenn` | GODKJENNER | docs/04 P3 |
| F-APPR-03 | Blocking prevents approval | Approval blocked while any BLOKKERENDE finding exists. | `FakturakjoringService.godkjenn` | — | docs/04 P3 |
| F-APPR-04 | Forkast (pre-export) | Discard a GENERERT/GODKJENT run. | POST `/kjoringer/{id}/forkast` | FORVALTER | docs/04 P3 |
| F-APPR-05 | One active run per period | Partial unique index; re-run requires forkasting the previous. | Flyway `V4` + `FakturakjoringService` | — | docs/03 §3 |
| F-APPR-06 | Exported run immutable | An EKSPORTERT run cannot be forkastet. | `FakturakjoringService.forkast` | — | docs/04 P3 |

## Export — LG04, PDF, CSV, XLSX

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-EXP-01 | Export from GODKJENT only | Export allowed only from GODKJENT; sets EKSPORTERT. | POST `/kjoringer/{id}/eksporter` · `EksportService` | GODKJENNER | docs/04 P4 |
| F-EXP-02 | LG04 fixed-width writer | 4324-char lines, fields at fixed byte offsets; byte-identical to the reference. | `export.lg04.Lg04Skriver` | — | docs/05 golden |
| F-EXP-03 | LG04 Windows-1252, no iconv | Written directly as Windows-1252. | `Lg04Eksport` | — | docs/02 |
| F-EXP-04 | LG04 assembly (one order per line) | Default: one LG04 order per invoice line (OQ-1-safe); 3 lines per order. | `EksportService.byggOrdrer` | — | OQ-1 |
| F-EXP-05 | Øre conversion in export only | ×100, round half-up, only here. | `EksportService` | — | docs/02 |
| F-EXP-06 | Kontering from product | account/dims/article from produkt (not hardcoded). | `EksportService` | — | docs/04 P4 |
| F-EXP-07 | Responsible from config | LG04 responsible persons from `EksportKonfig` (placeholders until OQ-2). | `EksportKonfig` | — | OQ-2 |
| F-EXP-08 | PDF detail per invoice | XHTML→PDF, zipped, named `<kundenummer>-<faktura_uuid>.pdf`. | `PdfEksport` | GODKJENNER | docs/04 P4 |
| F-EXP-09 | CSV export of lines | Run's lines as CSV. | `RegnearkEksport.csv` | GODKJENNER | docs/04 P4 |
| F-EXP-10 | XLSX export of lines | Run's lines as XLSX. | `RegnearkEksport.xlsx` | GODKJENNER | docs/04 P4 |
| F-EXP-11 | FileArchive port + local adapter | Artifacts archived behind a port; local filesystem adapter with SHA-256. | `archive.FileArchive` / `LocalFileArchive` | — | docs/02 |
| F-EXP-12 | eksportfil records | Each artifact recorded (type, filnavn, blob_url, sha256). | `EksportfilRepository` | — | docs/03 V4 |
| F-EXP-13 | Download export files | Download LG04/PDF/CSV/XLSX from the run page. | GET `/kjoringer/{id}/eksportfil/{filId}` | LESER | docs/04 P4 |

## Cross-cutting — audit, security, period, architecture

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-AUD-01 | Audit log (hendelseslogg) | Every state change recorded (who/what/when/details as jsonb). | `common.AuditService`, GET `/hendelseslogg` | LESER | docs/02 |
| F-SEC-01 | Roles LESER/FORVALTER/GODKJENNER | Authorization: writes require FORVALTER, approve/export require GODKJENNER, reads LESER. | `web.security.SecurityConfig` | — | docs/02 |
| F-SEC-02 | OIDC login (prod) | OIDC against any issuer (Entra ID); wired when an issuer is configured. | `SecurityConfig` | — | docs/02 |
| F-SEC-03 | Stubbed login (local) | Fixed dev user with all roles for offline use. | `StubAuthenticationFilter` (profile `local`) | — | docs/04 P0 |
| F-SEC-04 | CSRF protection | CSRF enforced on forms; multipart handled via query-string token. | `SecurityConfig` | — | docs/04 P2 |
| F-CORE-01 | Periode value type | Billing period is always an explicit first-of-month parameter, never derived from the clock. | `common.Periode` | — | docs/02 |
| F-ARCH-01 | Modulith layout | registry / usage / billing / export / archive / web / common with a defined dependency direction. | package layout | — | docs/02 |
| F-ARCH-02 | Runs fully offline | `docker compose up` = app + PostgreSQL + local archive + stubbed login, no Azure. | `docker-compose.yaml` | — | docs/02 |
| F-ARCH-03 | Config via environment | 12-factor; no value requires Azure. Profiles local/test/prod/demo. | `application*.yaml` | — | docs/02 |

## Non-functional & tooling

| ID | Feature | Description | Where | Role | Ref |
|---|---|---|---|---|---|
| F-NFR-01 | Flyway schema migrations | V1–V6 versioned in git; applied on start. | `db/migration` | — | docs/03 |
| F-NFR-02 | Testcontainers tests | 61 tests on real PostgreSQL 16 (no H2). | `src/test` | — | docs/05 |
| F-NFR-03 | LG04 golden-file tests | Byte-identical single line, 4324 invariant, Windows-1252, field offsets. | `Lg04SkriverTest` | — | docs/05 |
| F-NFR-04 | End-to-end scenario test | import→generate→approve→export verified. | `E2eScenarioIT` | — | docs/04 P5 |
| F-NFR-05 | Load sanity | 5000 customers × 6 products generate in < 1 min (~16 s measured). | `LoadIT` (`-Dload=true`) | — | docs/04 P5 |
| F-NFR-06 | CI: build/test/image | GitHub Actions: mvn verify + image publish (configurable registry). | `.github/workflows/ci.yml` | — | docs/04 P0 |
| F-NFR-07 | Actuator health | Liveness/readiness/health endpoints. | `/actuator/health` | — | docs/04 P0 |
| F-NFR-08 | Structured JSON logging | ECS JSON logs. | `application.yaml` | — | docs/02 |
| F-DEMO-01 | Demo dataset | `demo` profile seeds fake data covering every special case + imported usage. | `demo.Demodata` | — | docs/04 P5 |
| F-DEMO-02 | Ops runbook | Monthly procedure, re-run, DFØ-error handling, demo walkthrough. | `docs/runbook.md` | — | docs/04 P5 |
| F-DEMO-03 | User guide | Step-by-step onboarding for end users. | `docs/brukerveiledning.md` | — | — |

---

## Not built / deferred

Explicit gaps, so the mapping shows them clearly. These are intentional per docs/04 «Explicitly NOT in
MVP» and docs/06.

| ID | Item | Why | Ref |
|---|---|---|---|
| N-01 | Azure deployment (Container Apps/AKS, managed identity, Entra ID, Key Vault) | No environment available yet; seams are ready. | docs/06, runbook |
| N-02 | AzureBlobArchive adapter | Deliberately not shipped to avoid an untested Azure SDK dependency; `FileArchive` port is ready. | docs/02, docs/06 |
| N-03 | CRM / Dynamics 365 as customer source | Decision pending; local registry + `CustomerSource` seam. | OQ-5, docs/06 §2 |
| N-04 | Datavarehus direct read | Contract undefined; CSV is source + fallback. | OQ-4, docs/06 §1 |
| N-05 | Automated delivery to DFØ + receipt ingestion | Manual download/send in MVP. | OQ-8, docs/06 §3 |
| N-06 | Scheduling (cron generation) | Manual «Generer» in MVP; generation is a service method with an explicit period. | docs/06 §4 |
| N-07 | Avregning (corrections/crediting/retroactive prices) | Mechanism undefined. | OQ-6, docs/06 §5 |
| N-08 | LG04 multiple amount lines per order | Behind a flag; default is one order per line until confirmed. | OQ-1 |
| N-09 | Hosted PDF detail views for tjenesteeiere | PDFs are downloaded/distributed manually in MVP. | OQ-9, docs/06 §8 |
| N-10 | Onboarding old services (dp, e-signering, Altinn current, BOD) | Out of scope; period type isolated for future extension. | docs/06 §6 |
| N-11 | UX pass with real approvers | Needs the actual people. | docs/04 P5 |

## Open questions affecting scope

Facts not yet decided; the design stays safe while they are open (never invent answers — docs/07).

| OQ | Question | Affects features |
|---|---|---|
| OQ-1 | Does LG04 support multiple amount lines per order? | F-EXP-04, N-08 |
| OQ-2 | Kontering (konto, dims, artikkel_id) per product | F-REG-PROD-05, F-CTRL-05, F-EXP-06/07 |
| OQ-3 | SMS cost source & format | F-USG-03, F-USG-15 |
| OQ-4 | Datavarehus contract | F-USG-02, N-04 |
| OQ-5 | CRM readiness / temporary governance | F-REG-KUN-11, N-03 |
| OQ-6 | Avregning mechanism | N-07 |
| OQ-7 | Rules for deviating invoice recipient | F-REG-KUN-05 |
| OQ-8 | DFØ delivery channel + receipts; LG04 line separator | F-EXP-*, N-05 |
| OQ-9 | PDF retention / hosting | F-EXP-08, N-09 |
| OQ-10 | orgnr ↔ kundenummer mapping | F-GEN-04, F-REG-KUN-09/10 |
