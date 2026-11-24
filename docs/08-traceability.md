# 08 — Requirements traceability: MVP vs. stated requirements

Cross-reference of the implemented MVP (feature IDs from
[funksjonsoversikt.md](funksjonsoversikt.md)) against every requirement stated by the
organization. Requirement sources:

- **[LB]** "Løsningsbeskrivelse for fakturering" (the project lead, 2026-07-07) — component
  responsibilities and principles
- **[DE]** FinMod data-element sheet ("Kunde- og fakturainformasjon", Dataelementer) —
  mandatory elements, masters, and the FinMod business rules in the comments
- **[CM]** the project lead's mail answers (2026-07-07 and 2026-07-08) — scope, prices, timeline,
  Unit4 routine, PDF
- **[MVP]** Our MVP assumptions, stated in writing to the project lead (manual LG04 send, CSV
  upload, PDF download) — tacitly accepted unless contradicted

Status legend: ✅ covered · 🟡 covered via an **agreed temporary path** (permanent solution
tracked in docs/06/07) · 🔶 partial — needs confirmation or data from an owner · ❌ gap.

## Traceability matrix

| # | Requirement (source) | Features | Status | Notes |
|---|---|---|---|---|
| K-01 | Invoice the Altinn products: melding, formidling, varsling, autorisasjon, Studio incl. app-infrastructure [CM] | F-REG-PROD-02 | ✅ | Product codes must eventually match how usage data names them — verify against the first real extract (OQ-4). |
| K-02 | Forsystem fetches customer/agreement info from CRM [LB] | F-KUN-01..03, F-GEN-02, N-03 | ✅ | Through the DWH (Økonomi, 2026-11-24: «kunderegister = DWH = integrasjon mot CRM», B-24). Each import stores the DWH's name, kundenummer, fakturareferanse and bestillingsnummer per orgnr; forsystem has no customer registry (removed in specs/018). OQ-5 answered; what the DWH does not deliver is OQ-28. |
| K-03 | Forsystem fetches billable usage data from datavarehuset [LB] | F-USG-01..15, N-04 | 🟡 | CSV upload behind `UsageDataSource` port, stated in mail [MVP]. **Operational gap: nobody owns producing the monthly CSV extract from the DWH** until direct integration exists — must be assigned before December. |
| K-04 | Apply the current price list; products and prices registered and maintained directly in forsystem [LB, CM, DE rows Produktkatalog/Pris] | F-REG-PRIS-01..08, F-REG-PROD-01..05 | ✅ | Exactly the requested temporary master. Data task open: enter and activate the published 2027 price list (needs no code). |
| K-05 | Yearly price adjustment following the state budget (set in April) [CM] | F-REG-PRIS-02..07 | ✅ | Version lifecycle models the yearly cycle; next April = new UTKAST version. |
| K-06 | "Utfører nødvendige kontroller" [LB] | F-CTRL-02..09, F-CTRL-17 | 🔶 | The control *list* was never confirmed by the PO (asked, unanswered). Implemented: missing kundenummer, conflicting customer data, missing pris/kontering, deviation, zero amount, new customer. MANGLER_KUNDE, INAKTIV_AVTALE and AKTIV_KUNDE_UTEN_BRUK were retired in specs/018 (B-24). **Present the list for sign-off.** |
| K-07 | Send fakturagrunnlag to Unit4 following today's FEL routines [LB, CM] | F-EXP-01..07, F-EXP-13, N-05 | 🟡 | LG04 file, manual download + send [MVP]. Byte-format inherited from the proven old writer. OQ-8 answered (FEL contact, 2026-07-08): file goes **by email to regnskap in VIS** with periode, antall fakturaer and samlet beløp incl. mva; Unit4 import report validates counts/amounts/kundenumre; then EHF. Still open: OQ-1 (multi-line orders — safe default active) and OQ-11 (mva figure). **Real validation = December dry-run test import with the FEL contact/regnskap.** |
| K-08 | Tjenesteeier receives invoice + detail view of fakturagrunnlag [LB, CM] | F-EXP-08, N-09 | 🟡 | PDF per invoice, zip download, manual distribution [MVP]. Filename convention matches today's (`<kundenummer>-<uuid>.pdf`). Hosted detail view remains the FEL contact/FEL work [CM pkt 6]. |
| K-09 | Every invoice traceable back to usage data, agreements and prices [LB principle] | F-GEN-08/09, F-AUD-01, F-EXP-12 | ✅ | Structural: line → bruksdata + pris; run → prisversjon; snapshots; audit log; sha256 on exports. |
| K-10 | One authoritative source per element; data registered once, reused [LB principles] | F-KUN-01/02, F-USG-02, F-ARCH-01 | ✅ | Customer data has one master (CRM, via the DWH) and cannot be edited in forsystem (specs/018). Ports mean future masters (DWH API, product catalog) replace inputs without remodeling. |
| K-11 | Deviating fakturamottaker (another legal entity receives the invoice) [DE, FinMod] | F-GEN-03, F-GEN-08 | 🔶 | Not supported since specs/018: the fakturamottaker is the orgnr itself, since the DWH delivers no recipient. Økonomi dropped it with the registry; if needed, CRM/DWH must deliver it (OQ-28, OQ-7). Old invoices keep their snapshot. |
| K-12 | Fakturareferanse can vary per product/service for the same customer [DE, FinMod] | F-GEN-02 | 🔶 | Not supported since specs/018: one fakturareferanse per orgnr from the DWH. Dropped by Økonomi with the registry; if needed, CRM/DWH must deliver it (OQ-28). |
| K-13 | Multiple kundenummer per orgnr; shared kundenummer with distinct tilleggstekst; servicekode rules [DE, FinMod] | F-GEN-03, F-CTRL-17 | 🔶 | Not supported since specs/018: one kundenummer per orgnr from the DWH; rows that disagree block (MOTSTRIDENDE_KUNDEDATA). Dropped by Økonomi with the registry (OQ-28); VIS's mapping description (OQ-10) still pending. |
| K-14 | Multiple invoice lines per customer driven by business rules [DE row Fakturagrunnlag, FinMod] | F-GEN-01..14 | ✅ | Lines per (produkt, type); one invoice per orgnr (since specs/018; before: per (kunde, kundenummer)). |
| K-15 | Avtalestatus used as control before invoicing [DE row Avtalestatus] | F-USG-16/22 | ✅ | Checked upstream: the DWH flag filters out usage without an active agreement (OQ-4, OQ-25). Forsystem's own INAKTIV_AVTALE was retired in specs/018 (B-24). |
| K-16 | Orgnr and virksomhetsnavn validated against BRREG [DE comments, rows 1–2: "Valideres mot BRREG i dag"] | — | 🔶 | Forsystem's BRREG lookup served manual customer registration and was removed with it (specs/018). Orgnr and name are CRM's data, delivered through the DWH; whether CRM validates them against BRREG is not confirmed. The import still requires a 9-digit orgnr. |
| K-17 | Kundenummer mastered in Unit4 [DE] | F-KUN-02, F-CTRL-02 | ✅ | Forsystem takes Unit4-issued numbers as CRM delivers them through the DWH; it never generates or edits them. |
| K-18 | SMS costs traceable to the right tjenesteeier [DE row SMS] | F-USG-15, F-GEN-06, F-GEN-09 | 🔶 | Mechanically covered (per-orgnr rows → lines with bruksdata link). Source/master/format unresolved (OQ-3) — CSV columns provisional. |
| K-19 | Azure costs re-invoiced per FinMod from 2027-01-01 [DE, CM] | F-USG-15, F-GEN-06 | 🔶 | Same as K-18: mechanism ready, real data contract pending (OQ-4). |
| K-20 | Measurement from 2027-01-01; first run early February 2027 [CM] | F-CORE-01, all | 🔶 | Code is ready; the December dry-run needs: Azure test env (platform ticket [#3809](https://github.com/Altinn/altinn-platform/issues/3809), currently N-01), entered prices, kontering (OQ-2), a real usage extract, and the FEL contact. |
| K-21 | Temporary stewardship process for products/prices ("midlertidig forvaltningsprosess") [LB] | F-REG-*, F-AUD-01, F-DEMO-02/03 | 🔶 | Tooling (UI + roles + audit + guides) done; the *process* (who maintains what, when) must be agreed with Økonomi — an org task, not code. |

## Implemented beyond the stated requirements

Justified additions, no action needed unless the PO objects:

- **Prisversjon** (DE says "kan vente") — implemented anyway; it is what makes K-09
  traceability and K-05 the yearly cycle work. Negligible cost.
- **CSV/XLSX exports** (F-EXP-09/10) — supports verification workflows; no requirement.
- **Warning controls** STORT_AVVIK / NY_KUNDE / NULLBELOP — quality aids beyond the asked
  controls.
- **Audit log, demo dataset, runbook, user guide** — operational readiness.

## Verdict

Every stated requirement is now either covered, on an explicitly agreed temporary path, or tracked
as an open question with a safe default. Since specs/018 (2026-11-24) customer data comes from CRM
through the DWH; the FinMod special cases K-11..13 and forsystem's BRREG check (K-16) went with the
registry, by Økonomi's decision (OQ-28). The remaining work before the December dry-run is dominated by **data and process, not code**:

1. Enter + activate the 2027 price list (F-REG-PRIS UI; no code).
2. Get kontering values from Økonomi (OQ-2) — until then every run has blocking findings,
   by design.
3. FEL follow-up: LG04 multi-line (OQ-1), mva treatment for the email summary (OQ-11),
   and agree the December test import with regnskap/VIS. Send procedure itself is now
   known (OQ-8 answered).
4. Assign ownership of the monthly DWH→CSV extract (K-03) until direct integration.
5. K-16: confirm whether CRM validates orgnr/name against BRREG (forsystem no longer does).
6. OQ-28: Økonomi + CRM/DWH to say whether product-specific references, several kundenummer per
   orgnr or a deviating fakturamottaker are needed (K-11..13).
7. Platform environments ([altinn-platform#3809](https://github.com/Altinn/altinn-platform/issues/3809)) → deploy test before December (N-01).
