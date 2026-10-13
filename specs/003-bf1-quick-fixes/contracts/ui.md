# UI contract: Small fixes from Økonomi's BF1 findings

There are no new endpoints and no security changes.

| Page / endpoint | Change | Spec |
|---|---|---|
| `GET /prisversjoner/{id}` | Lists active products, plus inactive ones that have a price here (tagged "inaktiv"). Adds a "Brukes for …" sentence. | FR-001, FR-004 |
| `GET /prisversjoner` | Help text under the date fields of "Ny prisversjon". | FR-003 |
| `GET /produkter` | Active products first. New Dim 1/2/4 columns with configurable labels. | FR-005..007 |
| `GET /produkter/{id}` | Dim labels use the same configuration. | FR-007 |
| `GET /kjoringer/{id}` | Shows the new warning among *Advarsler* (no template change needed). | FR-008..010 |
| `GET /kjoringer/{id}/eksportfil/{filId}` | 400 `Regelbrudd` if the file is not this run's. Logs `LASTET_NED`. | FR-011, FR-012 |

## Texts (Norwegian, as shown)

- Help under the date fields: *«Datoene bestemmer hvilke perioder prisversjonen brukes for. En kjøring
  for en periode bruker den aktive versjonen som dekker periodens første dag. Begge datoer er
  inkludert, tom «til» betyr uten sluttdato, og to aktive versjoner kan ikke overlappe.»*
- On a version: *«Brukes for kjøringer fra januar 2027 og fremover.»* or *«… fra februar 2027 til og
  med desember 2027.»* or *«Dekker ingen perioder: en periode dekkes bare hvis dens første dag er
  innenfor datoene.»*
- The finding message: *«Kunde Oslo kommune (958935420) har aktiv avtale, men ingen bruk i perioden»*.
