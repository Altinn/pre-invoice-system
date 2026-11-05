# Quickstart: verify the invoice threshold

## Automated

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests: `TerskelverdiIT` (threshold per customer, strictly greater, finding + links, list,
downloads, correction exempt, price-version lock) and `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`.

## Manual (local demo)

1. `docker compose up --build`.
2. Prisversjoner → the active version shows «Terskelverdi 40,00 kr».
3. Kjøringer → Generer `2027-01`. Kontroller shows an advarsel `UNDER_TERSKEL` if any demo customer
   is at or under 40 kr; Fakturaer lists them under «Faktureres ikke (under terskelverdi)».
4. Download both lists as XLSX; the amounts are numbers.
5. Kontroller → Avstemming bruksdata: their rows are «ikke fakturert» with årsak `UNDER_TERSKEL`.
