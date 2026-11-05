# UI contract: specs/013

## Endpoints

| Method | Path | Role | Effect |
|---|---|---|---|
| POST | `/kjoringer/{id}/kontrollfunn/{funnId}/aksept` | FORVALTER, GODKJENNER | `akseptert=true` accepts, absent/false un-accepts one finding; redirect to `/kjoringer/{id}/kontroller#kontrollfunn` |
| POST | `/kjoringer/{id}/kontrollfunn/aksept` | FORVALTER, GODKJENNER | `kode=<KODE>`, `akseptert=true/false`: every ADVARSEL of that code in the run |
| POST | `/kjoringer/{id}/godkjenn` | GODKJENNER | now also refused while a warning is unaccepted |
| GET | `/kjoringer/{id}/{kontrollfunn\|avstemming-bruksdata\|ikke-fakturert}.{csv\|xlsx}` | any | list download, logged `LASTET_NED` |

A refusal (`Regelbrudd`: wrong status, blocking finding, finding of another run) shows the existing
error page with the message; LESER gets the existing 403 page.

## Kontroller tab

- Findings table gains a column «Funn akseptert»:
  - ADVARSEL group: checkbox (checked when all are accepted) + «N av M akseptert» when partial;
    with one finding, «Akseptert av <bruker>, dd.MM.yyyy HH:mm» under it.
  - BLOKKERENDE group: «Kan ikke aksepteres — rett og generer på nytt».
  - «Vis alle» list: one checkbox per finding with its message and the acceptance text.
  - Disabled checkboxes when the run is not GENERERT.
- «Hva betyr alvorlighet?»: Advarsel = must be accepted («Funn akseptert») before approval, but need not
  be fixed.
- Downloads: «Last ned (XLSX)» and «CSV» under Kontrollfunn, under «Per produkt og type» and under «Ikke
  fakturert» (the last two only when the run has a usage reconciliation and the list is not empty).

## Tab badge (all run tabs)

Blocking count (danger) if any; else unaccepted warnings (warning); else none.

## Oversikt (GENERERT)

- open warnings: `advarsel` item «N advarsler er ikke sjekket ut. Kryss av «Funn akseptert» på Kontroller
  før godkjenning.»
- accepted warnings: `ok` item «N advarsler, gjennomgått og sjekket ut. Hindrer ikke godkjenning.»
- none: «Ingen advarsler.»
- «Godkjenn» disabled while blocking or open; the note under it says which.

## Run list

Tags «N blokkerende», «N advarsler» (open only); quiet «N sjekket ut» when warnings are accepted.
