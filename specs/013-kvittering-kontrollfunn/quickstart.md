# Quickstart: verify specs/013

## Automated

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests:

- `KontrollfunnAkseptIT`: accept/un-accept one finding and a group, audit rows, blocking refused (service
  and DB check), LESER 403, frozen after approval, approval gate, regenerated run starts empty,
  correction run gated too, badge and Oversikt wording.
- `KontrollerEksportIT`: the three new lists in CSV/XLSX, logged, refused for correction runs.
- `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`: still green.

## Manual (local demo)

1. Start the app with the `local,demo` profile (PostgreSQL on localhost:5433).
2. Generate a run for the demo period. Open Kontroller: warnings have «Funn akseptert» checkboxes, blocking
   findings say they cannot be accepted.
3. Tick a warning: the badge drops, the row says who accepted it. Untick it: it is back.
4. Tick a group: all its findings are accepted. Open Historikk: one row per action.
5. Oversikt: «Godkjenn» is disabled until every warning is ticked; the checklist says «… gjennomgått og
   sjekket ut. Hindrer ikke godkjenning».
6. Download each list as XLSX and CSV from the Kontroller tab.
