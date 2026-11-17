---
tittel: Legg inn en kundenummerregel
sammendrag: Slik kobler du en kunde til et Unit4-kundenummer, også når flere kunder deler samme nummer og skilles med tilleggstekst.
oppdatert: 2026-11-17
rolle: FORVALTER
relatert: [slik-gjor-du/legg-til-fakturareferanse, forsta-systemet/slik-grupperes-fakturaer, oppslag/kontrollfunn, slik-gjor-du/avvikende-fakturamottaker]
nokkelord: [kundenummer, servicekode, tilleggstekst, MANGLER_KUNDENUMMER, Unit4, delt kundenummer]
---
En kundenummerregel sier hvilket Unit4-kundenummer bruken til en kunde skal faktureres på. Du trenger rollen FORVALTER for å legge dem inn eller slette dem.

> [!IMPORTANT]
> Kundesiden har ingen lenke i menyene i dag. Forvaltningen åpner den med en direkte lenke de har fått. Mangler du lenken, spør forvaltningen.

## Slik virker reglene

- Fakturaene deles per kunde og kundenummer: **én faktura per kundenummer**.
- En regel med et bestemt **Produkt** gjelder bare det produktet og går foran en regel med **(alle)**.
- Har en kunde to kundenummer, for eksempel ulikt per produkt, blir det to fakturaer.
- Finnes det ingen passende regel for et produkt kunden har bruk på, får kjøringen det blokkerende funnet `MANGLER_KUNDENUMMER`. Du kan ikke godkjenne før regelen er lagt inn.

## Gjør slik

1. Åpne kundesiden. Under **Kundenummerregler** ser du tabellen med reglene.
2. I skjemaet under tabellen fyller du ut:
   - **Kundenummer** (må fylles ut)
   - **Produkt**: velg **(alle)** eller et bestemt produkt
   - **Servicekode**, hvis kunden har det
   - **Tilleggstekst**, hvis flere kunder deler kundenummeret
3. Trykk [Legg til](knapp:).

Vil du fjerne en regel, trykker du [Slett](knapp:) på raden.

## Kunder som deler kundenummer

Noen kunder deler ett kundenummer i Unit4, for eksempel to virksomheter i samme etat. Legg da inn samme kundenummer på begge kunder, og skriv ulik **Tilleggstekst** på hver. Teksten skiller fakturaene fra hverandre i Unit4 og står på fakturagrunnlaget.

Hvor lang tilleggsteksten kan være, og hvordan den vises på fakturaen, er ikke endelig avklart (OQ-23). Hold den kort.

## Slik ser du at det gikk bra

Regelen står i tabellen. Når du genererer kjøringen, forsvinner funnet `MANGLER_KUNDENUMMER` for kunden, og fakturaen står under riktig kundenummer. Se [Kontrollfunn](../oppslag/kontrollfunn.md) hvis funnet blir stående.
