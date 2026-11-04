# Quickstart: verify invoice texts per run

## Automated

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests:

- `Lg04SkriverTest.fileIsByteIdenticalToFelsSample` — FEL's `test_3` still byte-identical (FR-010).
- `FakturateksterTest` — defaults, trim, limits 120/120/25, control characters, Windows-1252.
- `KjoringFakturateksterIT` — defaults at generation (ordinary and correction), edit + audit of only
  changed fields, no audit on no-op, refusal leaves texts unchanged, lock after approval / export /
  discard, the export writes the edited texts on every line, and the web form (FORVALTER may,
  GODKJENNER may not; refused input comes back with the message).

## Manual (local demo)

1. `docker compose up --build` (profile `local,demo`).
2. Kjøringer → Generer `2027-01`. The Oversikt tab shows «Tekster på fakturaen» with «Bruk av Altinn
   januar 2027», the samarbeid.digdir.no footer and `Servicedesk@altinn.no`.
3. Change the footer and save. Historikk shows an `ENDRET` row with `bunntekst` fra/til.
4. Try a 26-character Vår referanse (remove `maxlength` in the browser's dev tools): refused, the
   message names Vår referanse and 25.
5. Godkjenn: the section turns read-only. Eksporter → download LG04: positions 2510–2629 (1-based)
   hold the header, 2630–2749 the footer, 3265–3289 and 3290–3314 Vår referanse, on every line.
