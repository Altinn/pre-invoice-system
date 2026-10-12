# Data model: Small fixes from Økonomi's BF1 findings

**No migration.** Everything is either computed or fits existing columns.

| Change | Where | Stored? |
|---|---|---|
| `Kontrollkode.AKTIV_KUNDE_UTEN_BRUK` (ADVARSEL) | `kontrollfunn.kode` (free text, V4) | yes, as a normal finding row with `faktura_id` NULL |
| `LASTET_NED` for export files | `hendelseslogg` with `detaljer = {periode, type, filnavn}` | yes, an audit row |
| Covered periods of a price version | computed from `gyldig_fra`/`gyldig_til` | no |
| Dimension labels | environment configuration `forsystem.produkt.dimensjoner.*` | no |

**Covered-period rule** (mirrors `FakturakjoringService.aktivVersjonFor`):

- first = `gyldig_fra` if it is the 1st, else the 1st of the next month
- last = the 1st of `gyldig_til`'s month, or open
- the version covers no period if first > last
