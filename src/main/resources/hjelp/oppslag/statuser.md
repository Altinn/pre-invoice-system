---
tittel: Statuser
sammendrag: Oversikt over statusene for kjøringer, prisversjoner og bruksdata-importer, og hva de betyr.
oppdatert: 2026-11-17
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/3-generer, manedsrutinen/5-godkjenn, manedsrutinen/6-eksporter, slik-gjor-du/lag-prisversjon, oppslag/kontrollfunn]
nokkelord: [status, generert, godkjent, eksportert, forkastet, utkast, aktiv, arkivert, mottatt, validert, avvist]
---
## Kjøring

En kjøring går i én retning. Appen viser statusen med et enklere navn, og det interne navnet står ved siden av her.

{{livslop-kjoring}}

| Status i appen | Intern status | Betyr |
|---|---|---|
| Venter på godkjenning | GENERERT | Kjøringen er laget. Tekstene kan endres, og funnene må gås gjennom. |
| Klar til eksport | GODKJENT | Godkjent av en GODKJENNER. Tekstene er låst. |
| Eksportert | EKSPORTERT | Filene er laget og arkivert. Kjøringen kan ikke forkastes. |
| Forkastet | FORKASTET | Kjøringen er avsluttet og kan ikke godkjennes. Perioden kan genereres på nytt. |

En kjøring kan forkastes fram til den er eksportert. Se [Forkast og generer på nytt](../slik-gjor-du/forkast-og-generer-pa-nytt.md).

## Prisversjon

{{livslop-prisversjon}}

| Status | Betyr |
|---|---|
| UTKAST | Du kan endre priser og terskelverdi. Versjonen brukes ikke til generering ennå. |
| AKTIV | Brukes til å generere kjøringer for perioder den dekker. Priser og terskelverdi er låst. |
| ARKIVERT | Ikke lenger i bruk. Står igjen som historikk. |

En versjon aktiveres med knappen [Aktiver](knapp:), og en aktiv versjon arkiveres med [Arkiver](knapp:). Se [Lag prisversjon](../slik-gjor-du/lag-prisversjon.md).

## Bruksdata-import

Statusen står i tabellen Importer på [Bruksdata](/bruksdata).

| Status | Betyr |
|---|---|
| MOTTATT | Hentet fra datavarehuset og lagt til side. Den venter på at du åpner den og trykker [Bekreft import](knapp:). |
| VALIDERT | Bekreftet. Bruken er lagret, og kan ikke slettes eller endres. |
| AVVIST | Erstattet av en nyere import av samme periode. Radene beholdes for sporbarhet. |

Se [Hent bruksdata](../manedsrutinen/2-hent-bruksdata.md).
