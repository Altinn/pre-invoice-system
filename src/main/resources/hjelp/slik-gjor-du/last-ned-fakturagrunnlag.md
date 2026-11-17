---
tittel: Last ned fakturagrunnlag og lister
sammendrag: Oversikt over hvilke regneark du kan laste ned fra en kjøring, og hvor knappene står.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [slik-gjor-du/finn-en-faktura, manedsrutinen/6-eksporter, oppslag/filer]
nokkelord: [csv, xlsx, excel, regneark, nedlasting, fakturagrunnlag, fakturaliste]
---
Du kan laste ned fakturagrunnlaget og flere lister som regneark, også før kjøringen er godkjent. Det er nyttig når Økonomi vil kontrollere tallene i Excel. Alle som er logget inn, kan laste ned.

## Hele fakturagrunnlaget

1. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på kjøringen.
2. Gå til fanen [Filer](knapp:).
3. Trykk **Fakturagrunnlag (XLSX, for Excel)** eller **Fakturagrunnlag (CSV)**.

Filen har alle fakturalinjene i kjøringen. Beløp, antall og enhetspris er tall i Excel, så du kan summere dem. Filnavnet inneholder månedens status, for eksempel `GENERERT`, så et utkast ikke blir tatt for det godkjente grunnlaget.

For en forkastet kjøring kan fakturagrunnlaget ikke lastes ned.

## Lister fra de andre fanene

Hver liste har en XLSX-knapp og en CSV-knapp rett under tabellen.

| Liste | Fane | Knapp |
|---|---|---|
| Fakturalisten, én rad per faktura | Fakturaer | **Last ned fakturalisten (XLSX)** |
| Kunder som ikke faktureres under terskelverdien | Fakturaer | **Last ned listen (XLSX)** under «Faktureres ikke (under terskelverdi)» |
| Kontrollfunn med akseptert, av hvem og når | Kontroller | **Last ned kontrollfunn (XLSX)** |
| Fakturaer uten fakturareferanse | Kontroller | **Last ned listen (XLSX)** under «Fakturaer uten fakturareferanse» |
| Avstemming av bruksdata per produkt og type | Kontroller | **Last ned avstemmingen (XLSX)** |
| Bruksdata som ikke ble fakturert, med årsak | Kontroller | **Last ned ikke fakturert (XLSX)** |

Avstemmingen og listen over ikke fakturert finnes ikke for korrigeringskjøringer.

Linjene til én enkelt faktura laster du ned fra fakturasiden. Se [Finn en faktura](finn-en-faktura.md).

## Filene fra eksporten

Når kjøringen er eksportert, ligger LG04-filen, PDF-ene (som zip), CSV og XLSX i tabellen **Eksportfiler** på fanen Filer. Trykk [Last ned](knapp:) på raden. Se [Eksporter](../manedsrutinen/6-eksporter.md).

## Slik ser du at det gikk bra

Nettleseren laster ned en fil. Nedlastingen endrer ingenting i kjøringen, men hver nedlasting logges i [Hendelseslogg](hvem-endret-hva.md) med handlingen `LASTET_NED`.
