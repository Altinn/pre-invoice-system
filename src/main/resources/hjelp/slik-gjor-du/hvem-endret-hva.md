---
tittel: Finn ut hvem som har endret hva
sammendrag: Slik bruker du Hendelseslogg til å se hvem som gjorde hva og når, filtrere hendelsene og laste dem ned som regneark.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /hendelseslogg
apne-i-appen-tekst: Åpne Hendelseslogg
relatert: [slik-gjor-du/er-lg04-lastet-ned, forsta-systemet/sporbarhet, oppslag/roller]
nokkelord: [logg, audit, spor, endring, bruker, historikk, nedlasting]
---
Hendelseslogg er en skrivebeskyttet logg over alle endringer. Alle som er logget inn, kan se den. Ingen kan endre den.

## Hva som logges

Hver registerendring, import, generering, godkjenning, eksport og nedlasting. Det inkluderer også når noen krysser av «Funn akseptert» og når noen laster ned en fil.

## Gjør slik

1. Åpne [Hendelseslogg](/hendelseslogg). Listen viser de nyeste først, 50 per side. Bruk **Forrige** og **Neste** for å bla.
2. Velg filter og trykk [Filtrer](knapp:):
   - **Fra dato** og **Til dato**. Begge dagene er med.
   - **Bruker**. Skriv en del av brukernavnet. Store og små bokstaver er likegyldige.
   - **Entitet**, for eksempel `FAKTURAKJORING` for kjøringer.
   - **Handling**, for eksempel `GODKJENTE`.
3. Filtrene gjelder samtidig. Trykk [Nullstill filter](knapp:) for å se alt igjen.

Kolonnene er Tidspunkt (norsk tid), Bruker, Handling, Entitet, Id og Detaljer. Detaljene vises som «nøkkel: verdi», for eksempel en periode og antall fakturaer. Id er nummeret til det som ble endret, for eksempel kjøringen.

Filtrene står i adressen, så du kan sende en lenke til en filtrert visning.

## Last ned som regneark

Over tabellen står [Last ned hele loggen (XLSX)](knapp:). Har du valgt filter, heter knappen [Last ned utvalget (XLSX)](knapp:), og du får alle treffene, ikke bare siden du ser. [CSV](knapp:) gir det samme som tekstfil.

Regnearket har kolonnene tidspunkt, bruker, handling, entitet, entitet_id, periode, detaljer og detaljer_json. Perioden hentes fra detaljene når hendelsen gjelder en måned. Selve nedlastingen logges også, under entiteten `HENDELSESLOGG`.

## Se bare én kjøring

Åpne kjøringen og gå til fanen [Historikk](knapp:). Der står det som er logget for akkurat den kjøringen, nyeste først.

## Hvis noe går galt

- **«Ingen hendelser passer filteret»:** utvid datointervallet eller trykk **Nullstill filter**.
- **Fra-datoen er etter til-datoen:** du får en advarsel, og listen er tom. Rett datoene.
- **Du leter etter om noen har sendt LG04-fila:** loggen viser nedlastingen, ikke e-posten. Se [Er LG04-fila lastet ned?](er-lg04-lastet-ned.md).
