---
tittel: Endre tekstene på fakturaen
sammendrag: Slik endrer du topptekst, bunntekst og Vår referanse for en kjøring før den godkjennes.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [slik-gjor-du/forkast-og-generer-pa-nytt, manedsrutinen/5-godkjenn]
nokkelord: [topptekst, bunntekst, vår referanse, fakturatekst, LG04, tegn, lengde]
---
Hver faktura i en kjøring har tre tekster. De settes automatisk når du genererer kjøringen, og du kan endre dem så lenge kjøringen venter på godkjenning.

## Hva tekstene er

| Felt | Står | Standardtekst |
|---|---|---|
| **Topptekst** | Over fakturalinjene | «Bruk av Altinn» etterfulgt av kjøringens måned og år |
| **Bunntekst** | Under fakturalinjene | «Se samarbeid.digdir.no for priser og innsyn i egen statistikk» |
| **Vår referanse** | Kontaktpunkt kunden ser | Altinn-servicedeskens adresse |

Samme tekster brukes på alle fakturaene i kjøringen.

## Gjør slik

Du må ha rollen FORVALTER, og kjøringen må ha status «Venter på godkjenning».

1. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på kjøringen.
2. Bli på fanen [Oversikt](knapp:) og gå til **Tekster på fakturaen**.
3. Rett teksten i feltene.
4. Trykk [Lagre tekstene](knapp:).

Du ser meldingen «Tekstene er lagret.» Hver endring havner i fanen [Historikk](knapp:) med gammel og ny tekst.

## Grenser for lengde og tegn

Maksimal lengde i LG04-filen er 120 tegn for topptekst og bunntekst, og 25 tegn for Vår referanse. Teksten blir avvist hvis den:

- er tom
- er lengre enn grensen
- har linjeskift eller andre kontrolltegn
- har tegn LG04-filen ikke kan lagre, for eksempel emoji

Ved avvisning får du en melding, og det du skrev blir stående i skjemaet.

> [!WARNING]
> Regnskaps fakturamal foreslår kortere tekster på den trykte fakturaen, 90 tegn i topptekst og 65 i bunntekst. FEL har ikke bekreftet hvilken grense som gjelder (OQ-23). Er teksten lengre enn dette, lagres den likevel, men siden viser en advarsel. Sjekk den med regnskap før du godkjenner.

Lenker i bunnteksten er ikke klikkbare på fakturaen.

## Etter godkjenning

Tekstene låses når kjøringen godkjennes. Skal de endres etter det, må du [forkaste kjøringen og generere på nytt](forkast-og-generer-pa-nytt.md). Da starter tekstene fra standard igjen.

## Hvis noe går galt

- **Du ser ikke feltene, bare en tabell:** kjøringen er godkjent eller eksportert. Da er tekstene låst.
- **«Tekstene ble ikke lagret for denne kjøringen»:** kjøringen er eldre enn denne funksjonen. Tekstene kan ikke endres.
