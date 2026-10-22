# Data model: Run page in tabs

No schema change. There is one new repository finder,
`HendelsesloggRepository.findByEntitetAndEntitetIdOrderByTidspunktDescIdDesc(String, Long)`, which
reads the existing table.

There are three view records in `web`. They are never stored.

**`Fremdrift`**: the progress strip.

| Field | Meaning |
|---|---|
| `steg` | 4 × `Steg(navn, tilstand, hvem, naar)`, where `tilstand` is `FERDIG`, `NAA` or `SENERE` |
| `forkastet` | `Hendelse(hvem, naar)` when the run is FORKASTET, otherwise null |

Steps per status:

| Status | Generert | Kontrollert og godkjent | Eksportert | Sendt til regnskap |
|---|---|---|---|---|
| GENERERT | FERDIG | NAA | SENERE | SENERE |
| GODKJENT | FERDIG | FERDIG | NAA | SENERE |
| EKSPORTERT | FERDIG | FERDIG | FERDIG | NAA |
| FORKASTET | FERDIG | FERDIG if it was approved, otherwise SENERE | SENERE | SENERE, with `forkastet` set |

**`Fakturautvalg`**: one page of invoices.

| Field | Meaning |
|---|---|
| `rader` | `Rad(Faktura faktura, String kundenavn)`, at most 50 |
| `antallTreff`, `sumTreff` | count and total over all matches, not just this page |
| `side`, `antallSider` | page is 1-based. `antallSider` ≥ 1, so an empty result has 1 page |
| `q`, `sorter` | the normalised input, echoed back for the form and links |

**`Kjoringsrad`**: one row of the run list.

| Field | Meaning |
|---|---|
| `kjoring` | the run |
| `antallFakturaer` | number of invoices |
| `sumBelop` | total amount |
| `antallBlokkerende`, `antallAdvarsler` | number of blocking findings and warnings |
