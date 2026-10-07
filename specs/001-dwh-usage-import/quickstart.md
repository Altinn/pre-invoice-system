# Quickstart: validating the DWH usage import

## Prerequisites

- JDK 21, Docker (Colima: `DOCKER_HOST` set to its socket for Testcontainers).
- No access to the real DWH is needed.

## 1. Automated

```bash
./mvnw verify
```

Expected: green, including
- `DwhUsageClientContractTest` — fixture DAB responses (fake orgnrs): parse, `nextLink` refusal,
  non-200, malformed JSON, missing fields.
- `DwhAggregeringTest` — daily → monthly sums; several names → one product; unmapped names rejected;
  out-of-period rows rejected.
- `UsageImportServiceIT` (extended) — fetch → preview → confirm stores `kilde='DWH'`, archives the
  payload with sha256, replaces the previous import, honours the run blocker; staged `MOTTATT`
  import confirmed later re-validates.

## 2. Locally against a fixture

```bash
docker compose up
```

1. Start with `FORSYSTEM_DWH_ENABLED=true` and `DWH_BASE_URL` pointing at the fixture server
   (WireMock container or `src/test/resources/dwh/` served statically).
2. *Produkter → Kildenavn*: map the fixture's product names.
3. *Bruksdata*: pick 2026-09, click **Hent fra datavarehus** → preview shows monthly sums and any
   warnings → **Bekreft**.
4. Open the import: source DWH, fetch time, checksum; **Last ned rådata** returns the JSON.

## 3. Against the real DWH (once OQ-19 is resolved)

Point `DWH_BASE_URL` at the DAB endpoint in tt02, set `auth: entra` and the scope, map product names
confirmed under OQ-18, fetch the latest month and compare the preview's per-product totals with the
DWH team's own monthly totals. They must match exactly (SC-002).
