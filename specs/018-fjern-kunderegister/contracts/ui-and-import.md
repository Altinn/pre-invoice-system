# Contract: pages, routes and import formats after specs/018

## Routes removed (404 for everyone)

| Method | Path | Was |
|---|---|---|
| POST | `/bruksdata/last-opp/opprett-kunder` | «Opprett N kunder fra fila» |
| GET/POST | `/kunder/{id}` | customer detail (direct URL only) |
| POST | `/kunder/{id}/referanser`, `/referanser/{id}/slett` | fakturareferanser |
| POST | `/kunder/{id}/regler`, `/regler/{id}/slett` | kundenummer rules |

No `/api` route changes. The registry never had any.

## Bruksdata › Forhåndsvisning

Removed:
- the notice «N organisasjoner i fila finnes ikke i kunderegisteret …» and the create button with
  its hint text (`forhandsvis.html:19-35`, `liste.html:51`)
- the «Sjekk disse før du bekrefter» entries that compare DWH and registry values
  (`kryssjekkKunderegister`)

Added (only when it applies), as a plain line in the summary:
- «N organisasjoner har motstridende kundedata i fila (kundenummer eller fakturareferanse). De blir
  blokkerende funn når kjøringen genereres.» with the list of orgnr and values in a `<details>`.
- «N organisasjoner mangler kundenummer i fila.» with the list in a `<details>`.

Both are facts about the file. Neither blocks the import.

## Kjøring pages

- Kontroller tab: new code `MOTSTRIDENDE_KUNDEDATA`. «Slik løser du det» texts point to CRM and then a
  new import, not to forsystem pages (`kontroller.html:125-136` text «Legg inn referansen på kunden»
  changes).
- Invoice list and detail: name and orgnr come from the invoice row.

## Own CSV format (`docs/csv-format.md`)

Required columns unchanged: `periode, organisasjonsnummer, produktkode, type, antall, belop`.
New optional columns, matched by header name, any order:

| Column | Meaning |
|---|---|
| `kundenavn` | organisation name for the invoice |
| `kundenummer` | Unit4 kundenummer |
| `fakturareferanse` | «Deres referanse» |
| `bestillingsnummer` | PO number; empty ⇒ fakturareferanse |

The same orgnr with different non-blank values on several rows gives `motstridende`
(research D2).

## DWH API (`mv_altinn_usage_monthly`)

Fields read in addition to today: `organisation_name`, `bestillingsnummer`, both optional.
