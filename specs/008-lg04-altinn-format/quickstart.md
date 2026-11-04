# Quickstart: verify the Altinn LG04 format

## Automated

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests:

- `Lg04SkriverTest.fileIsByteIdenticalToFelsSample` — the whole file equals FEL's `test_3` (SC-001).
- `Lg04SkriverTest` — VALUE_1 blank without quantity, `0` for zero; header/footer cut at 120.
- `Lg04LeserTest` — the independent reader parses header + pairs and rejects broken structure.
- `EksportIT.anInvoiceWithTwoReferencesBecomesTwoOrders` — `DELT_REFERANSE` warning, run still
  approvable, two orders in the file (FR-009).
- `EksportAvstemmingIT.readerSeesSummaryEmailBlockAndReconciliation` — e-mail block shows område,
  buntnummer, totals ex. and incl. mva (FR-008, FR-011).

## Manual (local demo)

1. `docker compose up --build` (profile `local,demo`).
2. Kjøringer → Generer `2027-01`. On the control tab, expect an **Advarsel `DELT_REFERANSE`** for
   Trøndelag fylkeskommune (melding without reference, appinfra with `REF-APP-2027`).
3. Godkjenn → Eksporter → download the LG04 file. Check:
   - each customer starts with a line whose position 2494 (1-based) is `0`, followed by pairs
     numbered 1, 2…;
   - Trøndelag appears as two orders, the second carrying `REF-APP-2027` at position 26;
   - the appinfra amount line has blanks at positions 4225–4244.
4. The run page's "Til e-post til regnskap" shows Område, Buntnummer (today), Periode, Antall
   fakturaer and Samlet beløp eksl. and inkl. mva; no "uavklart" label anywhere.
