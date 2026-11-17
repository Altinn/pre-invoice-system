---
tittel: Kopier e-posten til regnskap
sammendrag: Slik henter du ferdig tekst med periode, antall fakturaer og beløp til følge-e-posten når LG04-fila skal sendes til regnskap.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/6-eksporter, slik-gjor-du/er-lg04-lastet-ned, oppslag/filer]
nokkelord: [e-post, epost, kopier, buntnummer, regnskap, LG04, følge-e-post]
---
LG04-fila sendes til regnskap på e-post. Forsystem lager teksten til e-posten, men sender den ikke selv.

## Hva e-posten inneholder

- Område: Altinn
- Buntnummer, som er datoen LG04-fila ble laget
- Periode
- Antall fakturaer i fila
- Samlet beløp eksl. mva og inkl. mva

Mva er 25 prosent og regnes per faktura, avrundet til øre. Regnskaps importrapport i Unit4 kan derfor avvike med noen øre.

## Gjør slik

Kjøringen må være eksportert. Før eksport vises ikke teksten.

1. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på den eksporterte kjøringen.
2. Bli på fanen [Oversikt](knapp:). Under **Neste steg: send e-post til regnskap** står **Til e-post til regnskap**.
3. Trykk [Kopier e-post](knapp:). Knappen skifter til «Kopiert».
4. Lim teksten inn i en ny e-post til regnskap.
5. Last ned LG04-fila fra fanen [Filer](knapp:) og legg den ved.

Adressen til regnskap står på Oversikt-siden under teksten.

## Slik ser du at det gikk bra

Knappen viser «Kopiert», og teksten ligger på utklippstavlen din. Det er ingen e-post som er sendt ennå. Du sender den fra e-postprogrammet ditt.

## Hvis noe går galt

- **Du finner ikke teksten:** sjekk at kjøringen har status «Eksportert». Teksten står på fanen Oversikt, ikke på Filer.
- **Buntnummeret står som «ukjent»:** se **LG04-avstemming** på fanen [Kontroller](knapp:) før du sender noe. Viser den en feil, kontakt forsystem-teamet.
- **Antall fakturaer er høyere enn du ventet:** en faktura med ulike referanser blir flere ordrer i LG04-fila. Tallet i e-posten er antall ordrer i fila, slik regnskap ser det.
- **Vil du sjekke om fila allerede er hentet:** se [Er LG04-fila lastet ned?](er-lg04-lastet-ned.md).
