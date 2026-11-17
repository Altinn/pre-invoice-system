---
tittel: Prøv selv
sammendrag: Hvordan du øver på en hel måned i testmiljøet eller lokalt uten å røre produksjon, og hva Nullstill periode sletter og beholder.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/1-sjekk-registrene, kom-i-gang/roller-og-tilgang, nar-noe-gar-galt/eksportert-kan-ikke-forkastes]
nokkelord: [tt02, test, øvelse, nullstill, demo, testmiljø]
---
Du kan øve på hele månedsrutinen uten at det får følger. Det gjør du i testmiljøet **tt02**, eller i en lokal kjøring av forsystem.

## tt02 og produksjon

tt02 og produksjon ser likedan ut og virker likedan. Forskjellen er hva du kan gjøre:

| | tt02 og lokalt | Produksjon |
|---|---|---|
| Hele månedsrutinen | Ja | Ja |
| En eksportert kjøring kan forkastes | Nei | Nei |
| Nullstill periode | Ja | Finnes ikke |

Nullstill periode finnes bare der testverktøyene er slått på: i tt02 og lokalt. I produksjon er eksporterte kjøringer endelige.

> [!WARNING]
> Send aldri en LG04-fil fra tt02 til DFØ. Den er laget for øving.

## Demodata

En lokal kjøring av forsystem kan starte med demodata, så du slipper å fylle registrene selv. Dataene er tydelig falske:

- produktene har kontering som begynner med «DEMO»
- prisversjonen heter «Prisliste 2027 (DEMO)»
- det ligger en importert bruksdatafil for januar 2027

Verdiene er plassholdere og sier ingenting om ekte kontering eller priser (OQ-2). I tt02 avgjør forvaltningen hva som er lagt inn, så sjekk registrene før du starter.

## Nullstill periode

Vil du kjøre samme måned om igjen, også etter eksport, bruker du **Testverktøy: nullstill periode** nederst på [Kjøringer](/kjoringer). Du trenger rollen FORVALTER. Klikk [Nullstill](knapp:) på raden for måneden, og bekreft med [Ja, nullstill](knapp:).

**Dette slettes for godt:**

- alle kjøringer for måneden, også eksporterte, med fakturaer og kontrollfunn
- eksportfilene og LG04-avstemmingen
- alle bruksdataimporter for måneden, også de bekreftede, og de arkiverte rådataene

**Dette beholdes:**

- produkter, kontering og kildenavn
- prisversjoner og priser
- kunder
- hendelsesloggen. Selve nullstillingen blir logget der.

Etterpå viser siden hvor mange kjøringer, fakturaer, importer og filer som ble slettet. Du kan så laste opp bruksdata og generere måneden på nytt.

> [!IMPORTANT]
> Nullstilling kan ikke angres. Bruk den bare i et testmiljø.

## En måned å øve på

Bruk en måned som ikke er en ekte fakturamåned i testmiljøet, for eksempel januar 2027 med demodata:

1. Sjekk at produktene har kontering og at en prisversjon er aktiv for måneden. Se [Sjekk registrene](../manedsrutinen/1-sjekk-registrene.md).
2. Last opp bruksdata for måneden og bekreft importen. Se [Hent bruksdata](../manedsrutinen/2-hent-bruksdata.md).
3. Generer kjøringen og se på kontrollfunnene.
4. Sjekk ut advarslene, godkjenn og eksporter. Du trenger rollen GODKJENNER for de to siste.
5. Se i [Hendelseslogg](/hendelseslogg) at hvert steg er logget.
6. Nullstill måneden og gjør den om igjen, til du er trygg.
