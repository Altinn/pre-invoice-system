---
tittel: Roller og tilgang
sammendrag: Hva rollene LESER, FORVALTER og GODKJENNER lar deg gjøre, og hva sidene «Ingen tilgang» og «Siden er utløpt» betyr.
oppdatert: 2026-11-17
relatert: [oppslag/roller, nar-noe-gar-galt/ingen-tilgang, nar-noe-gar-galt/siden-er-utlopt]
nokkelord: [rolle, tilgang, leser, forvalter, godkjenner, tillatelse]
---
Hva du kan gjøre i forsystem avhenger av rollen din. Rollene kommer fra innloggingen din, og du får dem av forvaltningen.

## De tre rollene

| Rolle | Du kan |
|---|---|
| **LESER** | Se alt: produkter, prisversjoner, bruksdata, kjøringer, filer og hendelseslogg. Du kan laste ned filer og regneark. Du kan ikke endre noe. |
| **FORVALTER** | Endre registre, hente og laste opp bruksdata, generere og forkaste kjøringer, endre tekstene på fakturaen og sjekke ut advarsler. |
| **GODKJENNER** | Godkjenne og eksportere kjøringer, og sjekke ut advarsler. |

Rollene bygger ikke på hverandre. GODKJENNER er ikke automatisk FORVALTER, og motsatt. Den som skal gjøre hele månedsrutinen, trenger begge rollene. Godkjenning og eksport er bevisst en egen oppgave.

Alle som er logget inn kan lese. Har du ingen rolle, kan du se, men ikke endre.

## Hvilken handling krever hvilken rolle

| Handling | Rolle |
|---|---|
| Se sider, laste ned fakturagrunnlag og eksportfiler | Alle innloggede |
| Opprette og endre produkter, kontering og kildenavn | FORVALTER |
| Opprette, endre, aktivere og arkivere prisversjoner | FORVALTER |
| [Forhåndsvis](knapp:), hente fra datavarehus, [Bekreft import](knapp:) | FORVALTER |
| [Generer](knapp:), [Forkast](knapp:), [Lagre tekstene](knapp:) | FORVALTER |
| Krysse av «Funn akseptert» | FORVALTER eller GODKJENNER |
| [Godkjenn](knapp:) | GODKJENNER |
| [Eksporter](knapp:) | GODKJENNER |
| Nullstille en periode (bare i testmiljø) | FORVALTER |

forsystem skjuler ikke knappene du mangler rolle til. Du ser dem, men får meldingen «Ingen tilgang» når du bruker dem. Det du prøvde å gjøre, blir ikke utført.

## Når tilgangen mangler

Prøver du noe du ikke har rolle til, får du siden **Ingen tilgang**. Den sier at endringer krever forvalter, og at godkjenning og eksport krever godkjenner. Les [Ingen tilgang](../nar-noe-gar-galt/ingen-tilgang.md) for hva du gjør videre.

Får du siden **Siden er utløpt**, ble skjemaet sendt fra en side som var for gammel. Ingenting er lagret. Les [Siden er utløpt](../nar-noe-gar-galt/siden-er-utlopt.md).

> [!TIP]
> I lokal demo er du logget inn som en testbruker med alle roller. Da får du aldri «Ingen tilgang».
