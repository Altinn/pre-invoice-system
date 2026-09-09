# 01 — Business context

## What is being invoiced

Digdir operates shared national services. From **2027-01-01** the new **FinMod price model**
applies to the **Altinn products**: melding, formidling, varsling, autorisasjon, and Studio
(including application infrastructure). Service owners (tjenesteeiere — public agencies and
municipalities) are invoiced **monthly**, based on **actual usage in the previous month**:
usage volume × unit price, plus pass-through costs (Azure, SMS).

The first invoicing run happens in **early February 2027** for January usage. Prices are set
once per year (following the state budget process, fixed in April of the preceding year) —
the 2027 price list is already published and can be loaded into the system now.

The older services (digital postkasse, e-signering, Altinn's current monthly billing, BOD)
are **out of scope**: they continue on the old system (`../agresso`), operated by FEL.

## The target architecture (from "Løsningsbeskrivelse for fakturering")

Flow: kildedata (measurement points and cost data in the Altinn products) → **datavarehus**
(collects, computes, checks against active agreements, filters non-billable usage, exposes
the billable data basis) → **forsystem (this project)** (fetches customer info, applies
prices, performs controls, produces fakturagrunnlag) → **Unit4** (generates the actual
invoice, bookkeeping, payment follow-up) → tjenesteeier receives the invoice plus a detail
view of the basis.

Principles from the document: one authoritative source per data element; data registered
once and reused; the warehouse decides *what* is billable, the forsystem decides *how* it is
billed; every invoice traceable back to usage data, agreements and prices.

The 2026-08-18 revision sharpens two boundaries that matter to us:

- **The warehouse attaches usage to the right tjenesteeier**, checks it against active
  agreements and filters out non-billable usage. Our `INAKTIV_AVTALE` control is therefore a
  second net, and usage coupled to the *wrong* orgnr is not something forsystem can detect
  (OQ-12 — how such errors are found and handled is unresolved, owned by Datavarehus + Kunde).
- **Kundenummer is checked in two places**: forsystem before the basis is sent, and Unit4 at
  LG04 import, which flags kundenummer that are *missing, unknown, wrong or deactivated*.
  Forsystem can only see "missing" (presence of a `kundenummer_regel`); the other three need
  Unit4's customer list, which we neither have nor have asked for.

### Authoritative sources (decided)

| Data | Master | Notes |
|---|---|---|
| Customer & agreement info (orgnr, name, fakturamottaker, fakturareferanse, bestillingsnummer, avtalestatus) | CRM (Dynamics 365) | **The agreed short-term solution lives in CRM** (2026-08-18), not in forsystem. The fields exist on the **Altinn integration/avtale** in the test environment: organisasjonsnummer, fakturaadresse/fakturamottaker, referanse, bestillingsnummer, kundenummer i Unit4, avtalestatus. Kundeteamet loads the real Altinn customer data once Økonomi confirms the field set; validation rules are being defined (OQ-13/14/15). **The interface CRM → forsystem is still unmapped and undated (OQ-5)**, so our registry remains the working source for the December dry-run and the offline fallback. |
| Usage & cost data (bruksvolum, Azure costs, fakturaperiode) | Datavarehus (**finopsdevsa** — PostgreSQL, operated by The platform team on the platform side; the "Datavarehus BOD" label in the løsningsbeskrivelse diagram is a misnomer) | Included in invoicing **from 2027-01-01**. Basis exposed as materialized views + a Data API Builder endpoint (`mv_altinn_usage_monthly`); API security layer pending (FEL↔platform). **Complete billable basis on the 6th of each month** — Azure costs are final 5 days into the following month. MVP uses CSV upload behind a port. |
| SMS costs | **Unresolved** (Varsling vs Datavarehus) | Source today: supplier extracts. Must be traceable to the right tjenesteeier. |
| Prices, price versions, product catalog | **Forsystem (this system)** | No authoritative product catalog exists or will exist in time; products and prices are registered and maintained manually here, with a temporary stewardship process. |
| Kundenummer, artikkel-ID, invoice number/status | Unit4 | Kundenummer is generated in Unit4 (**4 characters** — the FEL contact, 2026-08-18) and only then registered in CRM; new customers must be created in Unit4 first. orgnr↔kundenummer mapping is owned by VIS; description pending (OQ-10). **Artikkel-ID is created in Unit4 via DFØ (by December 2026, provisionally one per Altinn product) and registered in forsystem** — `produkt.artikkel_id` is a copy, not a master. |

## Business rules that shape the data model (from the FinMod data-element sheet)

These break the old system's assumptions (one line per customer, one reference per customer,
1:1 orgnr↔kundenummer) and must be native in the new model:

1. **Deviating invoice recipient**: another legal entity than the usage-generating orgnr can
   receive the invoice (example: Digitale Helgeland's usage invoiced to Brønnøy kommune).
   Rules for this are still being clarified (OQ-7).
2. **Product-specific invoice references**: fakturareferanse can differ per product for the
   same customer.
3. **Kundenummer is not 1:1 with orgnr**: one orgnr can have several kundenummer (per
   servicekode — example: Utdanningsdirektoratet with two), and several organizations can
   share one kundenummer distinguished by tilleggstekst (example: Politidirektoratet /
   Politi- og lensmannsetaten).
4. **Multiple invoice lines per customer**, driven by business rules (servicekode,
   tilleggstekst, product).
5. **Controls before invoicing**: avtalestatus must be active; orgnr/name validated against
   BRREG (Brønnøysundregistrene).

## The old system (`../agresso`) — what it teaches

Spring Boot batch tool (2019–present). Reads pre-aggregated per-customer amounts from
PostgreSQL functions, writes an **LG04** fixed-width file (3 lines per invoice: header line
"0", amount line "1", trailer; 4324 chars/line; fields at fixed byte offsets), registers a
per-invoice UUID URL in a `faktura_filer` table consumed by the PDF system
(faktura.digdir.no), and the operator manually converts UTF-8 → Windows-1252 (`iconv`) and
**emails the file to regnskap in VIS** with periode, antall fakturaer and samlet beløp
incl. mva stated in the mail body (FEL contact, 2026-07-08). Regnskap imports it into Unit4
(operated by DFØ); the import produces a report (invoice count, amounts, kundenummer
validity), and if correct the invoices go out to customers as EHF. Known defects to avoid
repeating:
billing period derived from "now" in static initializers; hardcoded accounting codes and
responsible persons; customers missing reference data silently get kundenummer 0;
secrets fetched by shelling out to 1Password CLI.

What we reuse: the byte-offset knowledge in `Linje1/2/3.java` and the golden-file tests that
lock the format character-by-character.

## MVP operating assumptions (deliberate simplifications)

- The LG04 file is **downloaded from the UI and emailed to regnskap in VIS manually** by a
  person, exactly as FEL does today (with periode, antall fakturaer and samlet beløp in the
  mail). No delivery automation, no receipt ingestion (the Unit4 import report is read by
  humans).
- PDF detail views are **generated and downloaded** from the UI; distribution to
  tjenesteeiere is manual. (Longer term this is the FEL contact's FEL work — see docs/06.)
- Usage data arrives as **CSV upload** through the UI until the datavarehus contract exists.
- No scheduler: a "Generate" button suffices for a monthly, human-approved process.

## People / owners

| Who | Role in this project |
|---|---|
| The project lead | Owns the løsningsbeskrivelse and coordinates the clarifications; answers scope/process questions |
| The platform team | Build and operate the datavarehus (finopsdevsa) and its usage API; access via the platform board |
| The FEL contact | Author & operator of the old system (FEL); technical contact for Unit4/DFØ transfer, LG04 details, PDF detail views; demoed admin.faktura.digdir.no |
| FinMod business lead | Source of the invoicing business rules above |
| VIS | Owns orgnr↔kundenummer mapping clarification |
| Økonomi | Owns fakturamottaker/fakturareferanse/bestillingsnummer decisions, kontering, prices; reviewed the CRM field set 2026-08-18 |
| The CRM administrator | Configures the CRM (D365) fields and their validation rules |
| Kundeteamet | Loads correct invoice information for the Altinn customers into CRM |
| Økonomi & VIS | Own the avregning (settlement) mechanism definition — restated 2026-08-18 as Økonomi + VIS |

## Glossary

| Term | Meaning |
|---|---|
| Forsystem | Pre-system: builds the invoice basis and sends it to the ERP; this project |
| Fakturagrunnlag | Invoice basis — the computed lines behind an invoice |
| Fakturakjøring | Invoicing run for one period |
| Tjenesteeier (TE) | Service owner — the customer being invoiced |
| LG04 | Fixed-width batch import format for Unit4/Agresso ERP (via DFØ) |
| DFØ | Norwegian gov agency for financial management; operates Unit4 for Digdir |
| Unit4 (Agresso) | The ERP that issues actual invoices and does bookkeeping |
| FEL | Digdir unit operating today's invoicing (the FEL contact) |
| FinMod | The finance/price model program this work belongs to |
| Kundenummer | Customer id in Unit4 (apar_id in LG04); **4 characters** (FEL contact, 2026-08-18) |
| Orgnr | Organisasjonsnummer, 9-digit legal entity id |
| Avtalestatus | Agreement status; must be active to invoice. In CRM only *aktiv*/*inaktiv*; the exact meaning of "aktiv" is unresolved (OQ-13) |
| Prisversjon | Versioned price list (yearly) |
| Bruksdata | Usage data (volume/costs) per orgnr, product, period |
| Avregning | Settlement/true-up mechanism — undefined, future scope |
| Bestillingsnummer | The customer's own PO/order number, printed on the invoice; Unit4's and the FinMod sheet's name for what CRM first called "kundens PO-/ordrenummer" |
| Artikkel-ID | Unit4 article number that drives correct kontering; mastered in Unit4, copied into `produkt` |
| BRREG | Brønnøysund register — validation source for orgnr/names |
