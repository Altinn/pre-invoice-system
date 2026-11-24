# Quickstart: validate specs/018

Prerequisites: Docker running (Colima: set `DOCKER_HOST`), JDK 21.

## Automated

```bash
mvn verify
```

Must pass, including:
- LG04 golden-file tests, byte-identical (SC-004)
- the V16 → V17 migration IT (orgnr and names backfilled, registry tables gone)
- the import ITs: no customer notice, no create route (404), conflicts and missing kundenummer counted
- the billing ITs: values from `bruksdata_kunde` reach invoice, lines and LG04 (SC-003);
  MANGLER_KUNDENUMMER and MOTSTRIDENDE_KUNDEDATA block approval
- the learning-center build checks (links, «Slik løser du det» for every code)

## Manual, locally

```bash
docker compose up
```

1. Bruksdata › Last opp: upload the flagged fixture `src/test/resources/dwh/eksport-flagg-3m.csv` for
   2026-10. The preview shows no customer-registry notice, no «Opprett … kunder» button, and no
   «kunderegisteret har …» warnings (SC-002). Confirm the import.
2. Kjøringer › Generer 2026-10. Open an invoice: name, kundenummer and fakturareferanse are the file's
   values. Download LG04 and check one organisation's kundenummer and reference.
3. Open `/kunder/1` and POST to `/bruksdata/last-opp/opprett-kunder`: both give 404 (SC-001).
4. Hjelp: no «Kunder» section. The month routine has no customer step (SC-005).
5. Open a run generated before the upgrade (tt02 copy): invoices, findings and exports as before.

## Before deploying to tt02

Dump the registry tables before V17 drops them (research D5):

```bash
pg_dump --data-only -t kunde -t kunde_referanse -t kundenummer_regel "$DATABASE_URL" > kunderegister-før-018.sql
```
