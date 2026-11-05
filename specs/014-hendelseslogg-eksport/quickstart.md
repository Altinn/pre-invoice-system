# Quickstart: verify specs/014

## Automated

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests:

- `DetaljtekstTest`: flat, nested, lists, null, periode.
- `HendelsesloggIT`: paging over 120 rows, each filter, combined filters, Oslo-day bounds, invalid range,
  XLSX (all rows, typed cells, autofilter) and CSV, download audit row, links keep filters.
- `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`: still green.

## Manual (local demo)

1. Start the app with `local,demo`; generate a run and change something so the log has rows.
2. Open Hendelseslogg: times are Norwegian, details readable, «Viser 1–N av N».
3. Filter on Handling = GENERERTE, then on a date range; page and download keep the filter.
4. Download XLSX: open it, check the filter arrows, the date-time column and the readable details.
