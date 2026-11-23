---
tittel: Last opp bruksdata fra en fil
sammendrag: Slik laster du opp en CSV-fil med bruk for en måned, leser forhåndsvisningen og bekrefter importen.
oppdatert: 2026-11-23
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [oppslag/csv-format, manedsrutinen/2-hent-bruksdata, nar-noe-gar-galt/import-blokkeres, nar-noe-gar-galt/fila-kjennes-ikke-igjen, slik-gjor-du/importer-periode-pa-nytt]
nokkelord: [csv, opplasting, forhåndsvisning, bekreft import, datavarehus, eksport]
---
Normalt henter du bruken fra datavarehuset. Opplasting av en fil er reserveløsningen, og den brukes også når en måned må rettes manuelt. Du trenger rollen FORVALTER for å laste opp og bekrefte. Alle med tilgang kan se importene.

## Før du starter

- Fila er enten forsystems eget CSV-format eller datavarehusets egen eksport av `mv_altinn_usage_monthly`. Formatet gjenkjennes automatisk, og forhåndsvisningen sier hvilket det ble.
- Last opp fila slik du fikk den. Åpner og lagrer du den i Excel, lagres den med semikolon, og da kjennes den ikke igjen.
- Finnes det en kjøring for perioden som ikke er forkastet, blokkeres importen. Se [Importer en periode på nytt](importer-periode-pa-nytt.md).

## Forsystems CSV-format

Kort fortalt: UTF-8, komma mellom kolonnene, punktum som desimaltegn, én rad per måling og en overskriftsrad. Kolonnene er `periode`, `organisasjonsnummer`, `produktkode`, `type`, `antall` og `belop`.

- `periode` er første dag i måneden, og må være den samme som perioden du velger.
- `type` er `BRUKSVOLUM` (fyll ut `antall`), `SMS_KOSTNAD` eller `AZURE_KOSTNAD` (fyll ut `belop`).
- `produktkode` må være en kode som finnes i produktregisteret.

Alle detaljer står i [CSV-format](../oppslag/csv-format.md). SMS- og Azure-radene er foreløpige (OQ-3, OQ-4).

## Gjør slik

1. Åpne [Bruksdata](/bruksdata).
2. Under «Last opp fil» velger du **Periode** (måned og år) og **Fil**.
3. Trykk [Forhåndsvis](knapp:). Ingenting lagres ennå.
4. Les forhåndsvisningen:
   - **Summer per produkt**: antall rader og summer. Sammenlign med fila.
   - **Avviste rader**: hver feil med linjenummer og årsak, for eksempel ukjent produktkode, ugyldig organisasjonsnummer, feil periode, duplikat eller negativ verdi.
   - **Advarsler**: avvik du bør se på, for eksempel at et produkt har endret seg mye fra forrige måned. De hindrer ikke import.
   - **Innholdet i fila** (kun datavarehusets eksport med flagg): hva som skjedde med hver rad i fila. Se under.
5. Er alt i orden, trykker du [Bekreft import](knapp:). Vil du avbryte, trykker du [Avbryt](knapp:).

> [!WARNING]
> En bekreftet import kan ikke slettes eller endres. Sjekk summene før du bekrefter.

## Eksport med flagg fra datavarehuset

Har eksporten kolonnen `flag_inv_altinn`, er det datavarehuset som avgjør hva som skal faktureres. Forsystem bruker bare rader med flagget 1 som ligger i måneden du valgte. Resten telles og vises i «Innholdet i fila»:

- **Brukt**: rader merket for Altinn-fakturering i perioden.
- **Utenfor perioden**: rader fra andre måneder. Det er ikke en feil. Last fila opp én gang per måned.
- **DPV (ikke fakturert her)**: rader med DPV-flagg.
- **Utelatt av datavarehuset**: rader uten flagg, med datavarehusets egen årsak (kolonnen `regel`).
- **Kontrollsum**: summen av gruppene er alltid lik antall rader i fila. Stemmer den ikke, er noe galt med fila.

Er flagget noe annet enn 0 eller 1, eller tomt, avvises raden med linjenummer, kolonne og verdi. Har en rad som skal brukes et produktnavn forsystem ikke kjenner, står det hvilket navn du må koble under Produkter → Kildenavn.

## Alt eller ingenting

Har fila én avvist rad, kan den ikke importeres. Siden sier «Import er ikke mulig før alle avvik og blokkeringer er løst». Rett fila, og last den opp på nytt. Ingenting lagres før hele fila er gyldig.

## Slik ser du at det gikk bra

Du er tilbake på [Bruksdata](/bruksdata), og importen står i tabellen med status VALIDERT og antall rader. Neste steg er å generere kjøringen, se [Generer kjøringen](../manedsrutinen/3-generer.md).

## Hvis noe går galt

- Forhåndsvisningen sier at organisasjoner mangler i kunderegisteret: se [Opprett kunder fra fila](opprett-kunder-fra-fila.md).
- Et ukjent produktnavn fra datavarehuset: se [Koble et kildenavn](koble-kildenavn.md).
- Forsystem kjenner ikke igjen fila: se [Fila kjennes ikke igjen](../nar-noe-gar-galt/fila-kjennes-ikke-igjen.md).
- Importen er blokkert: se [Import blokkeres](../nar-noe-gar-galt/import-blokkeres.md).
