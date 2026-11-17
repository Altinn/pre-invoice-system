---
tittel: Forkast en kjøring og generer på nytt
sammendrag: Slik forkaster du en kjøring som ikke er eksportert, retter feilen og genererer måneden på nytt.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/3-generer, manedsrutinen/4-kontrollfunn, nar-noe-gar-galt/eksportert-kan-ikke-forkastes, slik-gjor-du/lag-korrigeringskjoring]
nokkelord: [forkast, forkastet, på nytt, regenerer, rette feil]
---
Du kan bare ha én aktiv kjøring per måned. Vil du generere på nytt, for eksempel etter å ha rettet et register, må du først forkaste den gamle.

## Når bruker du dette

- Kontrollene viser blokkerende funn, og du har rettet registeret eller bruksdata.
- Du fant en feil i en pris, en kontering eller en fakturareferanse etter at kjøringen ble generert.
- Du vil endre tekstene etter at kjøringen er godkjent.

## Gjør slik

Du må ha rollen FORVALTER. Kjøringen må ha status «Venter på godkjenning» eller «Klar til eksport».

1. Rett feilen først, for eksempel i produktregisteret, prisversjonen eller kundene.
2. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på kjøringen.
3. Trykk [Forkast](knapp:) og bekreft med [Ja, forkast](knapp:).
4. Kjøringen får status «Forkastet». Trykk knappen som heter «Generer» etterfulgt av måneden og «på nytt».

Du kommer til den nye kjøringen. Funnene er regnet ut på nytt.

> [!IMPORTANT]
> En ny kjøring starter uten avkryssede advarsler og med standardtekstene. Du må [sjekke ut advarslene](sjekk-ut-advarsler.md) og eventuelt endre [tekstene](endre-fakturatekster.md) på nytt.

## Hva forsvinner ikke

Den forkastede kjøringen blir stående i listen med status «Forkastet», og alt som er logget om den står i Historikk. Bruksdata, kunder og priser berøres ikke.

## Hvis noe går galt

- **«Det finnes allerede en aktiv kjøring for måneden»:** forkast den eksisterende kjøringen først.
- **«Ingen bruksdata»:** importer bruksdata for måneden før du genererer.
- **Forkast-knappen mangler:** kjøringen er eksportert. Den kan ikke forkastes, se [Eksportert kan ikke forkastes](../nar-noe-gar-galt/eksportert-kan-ikke-forkastes.md). Trenger bruken å rettes etter eksport, se [Lag en korrigeringskjøring](lag-korrigeringskjoring.md).
- **Testverktøy:** i testmiljøet finnes også «Testverktøy: nullstill periode» nederst på Kjøringer. Det sletter alt for måneden for godt, også eksporterte kjøringer. Det finnes ikke i produksjon.
