# Quickstart: verify fakturareferanse control and fallback

## Automated

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests: `FakturareferanseIT` (fallback in lines and LG04 header, registry untouched, no new split,
split kept for different references, warning per invoice, product row, threshold/no-usage customers not
listed, Kontroller tab and downloads) and `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`.

## Manual (local demo, profiles `local,demo`)

1. Kunder → Oslo kommune: reference `DEMO-OSLO-REF`, bestillingsnummer «— (fakturareferansen brukes)».
2. Kjøringer → Generer 2027-01 → Kontroller: advarsler `MANGLER_FAKTURAREFERANSE` for the demo customers
   without references; section «Fakturaer uten fakturareferanse» lists them; download XLSX/CSV.
3. Godkjenn → Eksporter. In `data/arkiv/kjoring-<id>/`, Oslo kommune's header line (offset 2493 = `0`)
   has `DEMO-OSLO-REF` at offsets 25 and 2368 (width 25).
