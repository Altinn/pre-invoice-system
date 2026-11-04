# UI contract: invoice texts on the run page

## GET `/kjoringer/{id}` (Oversikt) — section `#tekster` «Tekster på fakturaen»

Between "Neste steg" and "Sammendrag". Visible to every role.

- **GENERERT**: a form (`POST /kjoringer/{id}/tekster`) with three fields:
  - `topptekst` — label «Topptekst», `maxlength=120`, help «Maks 120 tegn i LG04-filen. Regnskaps
    mal foreslår maks 90 på trykt faktura (ikke bekreftet, OQ-23).»
  - `bunntekst` — label «Bunntekst», `maxlength=120`, help as above with 65.
  - `varReferanse` — label «Vår referanse», `maxlength=25`, help «Maks 25 tegn i LG04-filen.»
  - button «Lagre tekstene». A warning under header/footer when the stored text is above the
    advisory printed limit.
- **GODKJENT / EKSPORTERT / FORKASTET**: the three values read-only, with «Låst etter
  godkjenning.»; if the run has no stored texts (before V13): «Tekstene ble ikke lagret for denne
  kjøringen.»

## POST `/kjoringer/{id}/tekster` — role FORVALTER (existing rule for POST)

Parameters: `topptekst`, `bunntekst`, `varReferanse`.

| Outcome | Response |
|---|---|
| Stored (or no change) | 302 → `/kjoringer/{id}#tekster`, flash `teksterLagret` |
| Validation refused (FR-005) | 302 → `/kjoringer/{id}#tekster`, flash `teksterFeil` (message) and `innsendteTekster` (the submitted values, shown in the form) |
| Run not GENERERT (FR-007) | 302 → `/kjoringer/{id}#tekster`, flash `teksterFeil` «Tekstene er låst etter godkjenning …» |
| Role LESER/GODKJENNER only | 403 (Spring Security, unchanged) |

Nothing is stored and no audit row is written when the outcome is a refusal.
