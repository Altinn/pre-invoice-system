---
tittel: Koble et kildenavn fra datavarehuset til et produkt
sammendrag: Slik forteller du forsystem hvilket produkt og hvilken type et produktnavn fra datavarehuset hører til.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /produkter/kildenavn
apne-i-appen-tekst: Åpne Kildenavn
relatert: [slik-gjor-du/last-opp-csv, slik-gjor-du/rett-kundedata, manedsrutinen/2-hent-bruksdata, nar-noe-gar-galt/import-blokkeres]
nokkelord: [produktnavn, mapping, datavarehus, kobling, ukjent produktnavn]
---
Datavarehuset bruker egne produktnavn, for eksempel «Varsling e-post». Under Kildenavn kobler du hvert navn til et produkt i forsystem og en type. Et navn uten kobling avviser importen. Du trenger rollen FORVALTER for å endre koblingene.

## Når trenger du dette?

- Importen avvises fordi et produktnavn er ukjent.
- Datavarehuset har tatt i bruk et nytt produktnavn.

Koblingene gjelder både når du henter fra datavarehuset og når du laster opp datavarehusets egen eksportfil. Forsystems eget CSV-format bruker produktkoder og trenger ingen kobling.

## Gjør slik

1. Åpne [Kildenavn](/produkter/kildenavn). Du finner siden også via lenken «Kildenavn» på [Produkter](/produkter).
2. Se i tabellen om navnet allerede er koblet.
3. Under «Ny kobling» fyller du ut:
   - **Kildenavn**: nøyaktig slik det står i datavarehuset.
   - **Produkt**: produktet det skal høre til.
   - **Type**: `BRUKSVOLUM`, `SMS_KOSTNAD` eller `AZURE_KOSTNAD`.
4. Trykk [Legg til](knapp:).

Flere kildenavn kan kobles til samme produkt og type. Da summeres de. Et kildenavn kan bare kobles én gang.

## Fjerne en kobling

Trykk [Fjern](knapp:) på raden. Importer du etter dette, avvises navnet igjen til det er koblet på nytt.

> [!IMPORTANT]
> Hvilke kildenavn som hører til hvilket produkt og hvilken type, må produkteierne bekrefte (OQ-18). Det gjelder særlig varslingsproduktene og Studio. Spør produkteier hvis du er usikker.

## Slik ser du at det gikk bra

Koblingen står i tabellen med riktig produkt og type. Neste import av en datavarehusfil eller en henting godtar navnet. Importen viser da produktet under «Summer per produkt» i forhåndsvisningen.
