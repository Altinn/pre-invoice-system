---
tittel: Hent bruksdata
sammendrag: Du henter måneden fra datavarehuset, eller laster opp en fil, sjekker forhåndsvisningen og bekrefter importen.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [manedsrutinen/1-sjekk-registrene, manedsrutinen/3-generer, slik-gjor-du/last-opp-csv, slik-gjor-du/opprett-kunder-fra-fila, slik-gjor-du/importer-periode-pa-nytt, oppslag/csv-format]
nokkelord: [import, datavarehus, csv, bekreft import, forhåndsvisning, mottatt]
---
Bruksdata er antall transaksjoner og kostnader per organisasjon og produkt for måneden. Du henter dem normalt fra datavarehuset. Å laste opp en fil er reserveløsningen.

## Hvem

Du trenger rollen FORVALTER. Med LESER kan du se importene, men ikke hente eller bekrefte.

## Før du starter

- [Registrene er sjekket](1-sjekk-registrene.md), særlig kildenavnene.
- Datavarehusets grunnlag er komplett den 6. i måneden etter målemåneden, fordi Azure-kostnadene først er endelige da (OQ-4). Vent derfor til den 7. før du henter.
- Finnes det allerede en kjøring for perioden som ikke er forkastet, blokkeres importen. Forkast kjøringen først.

## Gjør slik

### Hent fra datavarehuset

Knappen vises bare når koblingen til datavarehuset er slått på.

1. Åpne [Bruksdata](/bruksdata).
2. Velg **Periode** og trykk [Hent fra datavarehus](knapp:). Forsystemet summerer dagene til én rad per organisasjon, produkt og type.
3. Har oppsettet automatisk henting slått på, kan måneden allerede ligge i tabellen Importer med status **MOTTATT**. Trykk [Åpne](knapp:) på den raden i stedet.

### Last opp en fil

Bruk dette hvis datavarehuset ikke kan brukes.

1. Velg **Periode** og **Fil** under Last opp fil, og trykk [Forhåndsvis](knapp:).
2. Last opp fila slik du fikk den. Ikke åpne og lagre den i Excel først, for da kjennes den ikke igjen.
3. Forsystemet gjenkjenner formatet selv, enten forsystemets eget CSV-format eller datavarehusets eksport. Forhåndsvisningen sier hvilket det ble. Formatet er beskrevet i [CSV-format](../oppslag/csv-format.md) og [Last opp CSV](../slik-gjor-du/last-opp-csv.md).

### Sjekk forhåndsvisningen og bekreft

1. Les **Advarsler**. De hindrer ikke import, men bør sjekkes, for eksempel at et produkt har endret seg mye fra forrige måned.
2. Sammenlign **Summer per produkt** med det du forventer.
3. Mangler noen organisasjoner i kunderegisteret, sier forhåndsvisningen det. Trykk [Opprett kunder fra fila](knapp:) hvis det er riktig. Se [Opprett kunder fra fila](../slik-gjor-du/opprett-kunder-fra-fila.md).
4. Trykk [Bekreft import](knapp:).

> [!WARNING]
> En bekreftet import kan ikke slettes eller endres. Er noe feil, laster du opp måneden på nytt. Da erstattes den gamle importen. Se [Importer periode på nytt](../slik-gjor-du/importer-periode-pa-nytt.md).

## Slik ser du at det gikk bra

Importen står i tabellen Importer med status **VALIDERT**, og antall rader stemmer med det du forventet. Nederst på siden står en lenke til Kjøringer. Fortsett til [Generer kjøringen](3-generer.md).

## Hvis noe går galt

- **Ukjent produktnavn avvises.** Koble navnet under [Kildenavn](/produkter/kildenavn), og hent på nytt.
- **Fila har avviste rader.** Fila importeres alt eller ingenting. Rett feilene som står under Avviste rader, og last opp på nytt.
- **Fila kjennes ikke igjen.** Feilmeldingen viser hvilke kolonner fila har, og hvilke som var forventet. Se [Fila kjennes ikke igjen](../nar-noe-gar-galt/fila-kjennes-ikke-igjen.md).
- **Import blokkeres.** Se [Import blokkeres](../nar-noe-gar-galt/import-blokkeres.md).
- **«Ingen bruksdata i datavarehus ennå».** Måneden er ikke klar der. Vent, eller bruk en fil.
