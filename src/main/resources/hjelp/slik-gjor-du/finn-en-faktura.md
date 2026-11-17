---
tittel: Finn en faktura i en kjøring
sammendrag: Slik søker du etter en faktura, sorterer listen, ser fakturalinjene og laster ned linjene til én faktura.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [slik-gjor-du/last-ned-fakturagrunnlag, forsta-systemet/slik-grupperes-fakturaer, oppslag/statuser]
nokkelord: [søk, sortere, faktura, linjer, ordre, kundenummer, orgnr]
---
Alle fakturaene i en kjøring står på fanen **Fakturaer**. Alle som er logget inn, kan se dem.

## Gjør slik

1. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på kjøringen.
2. Gå til fanen [Fakturaer](knapp:). Tallet ved fanen er antall fakturaer.
3. Skriv i feltet **Søk i fakturaene**. Du kan søke på kundenavn, organisasjonsnummer, kundenummer eller ordrenummer. Listen oppdateres mens du skriver, eller du kan trykke [Søk](knapp:).
4. Trykk på en kolonneoverskrift for å sortere.

Over tabellen står antall fakturaer og sum eksl. mva for treffene.

## Sortering

| Overskrift | Rekkefølge |
|---|---|
| **Ordre** | Stigende ordrenummer (standard) |
| **Kunde** | Alfabetisk etter kundenavn |
| **Sum (eksl. mva)** | Størst beløp først |

Listen viser 50 fakturaer per side. Bruk **Forrige** og **Neste** nederst. Søk og sortering står i adressen, så du kan dele en lenke til en filtrert visning.

## Åpne en faktura

Trykk [Linjer](knapp:) på raden. Du ser kundenavn, kundenummer, mottakerens organisasjonsnummer, sum og referanse øverst, og alle fakturalinjene med produkt, beskrivelse, antall, enhetspris og beløp. Hver linje har også bruksdata-id og pris-id, så du kan spore den tilbake til kilden. Kontrollfunn som gjelder fakturaen, vises øverst.

Står det flere referanser øverst, er fakturaen delt på referanse. Se [Slik grupperes fakturaer](../forsta-systemet/slik-grupperes-fakturaer.md).

## Last ned linjene til én faktura

På fakturasiden, over linjene, står **Last ned linjene** med valgene **XLSX** og **CSV**. Filen har en sumrad nederst. Nedlastingen endrer ingenting, men den logges i [Hendelseslogg](hvem-endret-hva.md).

## Hvis noe går galt

- **«Ingen fakturaer treffer søket»:** prøv en kortere del av navnet, eller trykk **Vis alle fakturaer**.
- **En kunde du venter, mangler:** kunden kan ha vært under terskelverdien eller uten bruk i måneden. Se listen **Faktureres ikke (under terskelverdi)** nederst på fanen Fakturaer. Se også [Last ned fakturagrunnlag](last-ned-fakturagrunnlag.md) for listene som kan lastes ned.
