---
tittel: Ingen bruksdata for perioden
sammendrag: Hva du gjør når generering stopper fordi det ikke finnes importert bruksdata for måneden.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [manedsrutinen/2-hent-bruksdata, slik-gjor-du/last-opp-csv, nar-noe-gar-galt/fila-kjennes-ikke-igjen]
nokkelord: [bruksdata, import, mangler, periode, mottatt, bekreft]
---
## Hva du ser

Når du trykker [Generer](knapp:), får du siden «Kan ikke fullføre» med meldingen «Ingen bruksdata for 2027-01 — importer bruk først.»

Henter du fra datavarehuset og det ikke finnes data ennå, står det i stedet «Ingen bruksdata i datavarehus for januar 2027 ennå».

## Hvorfor

En kjøring bygges fra bruksdata. Uten en **bekreftet import** for nøyaktig den perioden er det ingenting å fakturere. Vanlige årsaker:

- Importen er ikke gjort for denne måneden.
- Importen har status MOTTATT. Den er hentet automatisk, men venter på at du trykker [Bekreft import](knapp:).
- Du valgte feil periode i skjemaet.
- Du hentet fra datavarehuset for tidlig. Grunnlaget er først komplett etter den 6. i måneden etter.

## Hva du gjør

1. Åpne [Bruksdata](/bruksdata) og se i listen **Importer** om perioden finnes, og hvilken status den har.
2. Er importen MOTTATT, åpne den, se gjennom forhåndsvisningen og trykk [Bekreft import](knapp:).
3. Finnes den ikke, hent fra datavarehuset eller last opp en fil. Se [Hent bruksdata](../manedsrutinen/2-hent-bruksdata.md) og [Last opp en CSV-fil](../slik-gjor-du/last-opp-csv.md).
4. Gå tilbake til [Kjøringer](/kjoringer), velg samme periode og trykk [Generer](knapp:).

> [!TIP]
> Meldingen om bruksdata kommer før meldingen om prisversjon. Når begge mangler, løser du bruksdata først.
