---
tittel: Legg inn kontering for et produkt
sammendrag: Slik fyller du ut artikkel-id, konto og dimensjoner på et produkt, og hvorfor tomme felt stopper godkjenningen.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /produkter
apne-i-appen-tekst: Åpne Produkter
relatert: [oppslag/kontrollfunn, nar-noe-gar-galt/godkjenn-er-gra, manedsrutinen/1-sjekk-registrene]
nokkelord: [konto, dimensjon, artikkel-id, koststed, kappost, bokføring, MANGLER_KONTERING]
---
Hvert produkt har en kontering: artikkel-id, konto og dimensjonene. Verdiene skrives rett inn i LG04-fila ved eksport. Du trenger rollen FORVALTER for å endre dem.

> [!IMPORTANT]
> Hvilke verdier som er riktige, er ikke endelig bekreftet (OQ-2). Fyll bare inn det Økonomi har oppgitt. Gjett aldri en kode. Et tomt felt gir et tydelig stopp, mens en feil kode kan gå uoppdaget gjennom til regnskapet. Denne artikkelen oppgir derfor ingen eksempelverdier.

## Gjør slik

1. Åpne [Produkter](/produkter).
2. Trykk [Rediger](knapp:) på raden til produktet.
3. Under **Kontering** fyller du ut feltene du har fått verdier for:
   - **Artikkel-id** (tall)
   - **Konto**
   - **Dim1 Koststed**
   - **Dim2 Prosjekt/formål**
   - **Dim4 Kappost**
4. Trykk [Lagre](knapp:).

Navnene på dimensjonene er bare visningsnavn i skjermbildet. Det som lagres og eksporteres, er verdiene.

Gjenta for hvert produkt som skal faktureres. Produkter kan dele verdier, for eksempel artikkel-id, hvis Økonomi har bestemt det.

## Hvorfor tomt konto stopper deg

Har et produkt med bruk ingen **Konto**, får kjøringen et blokkerende funn (`MANGLER_KONTERING`). Du kan da ikke godkjenne. Det er med vilje, så du ikke ved et uhell fakturerer uten kontering. Se [Kontrollfunn](../oppslag/kontrollfunn.md) for hva funnet betyr og hvordan det løses.

> [!WARNING]
> Kontrollen ser i dag bare etter **Konto**. Står artikkel-id eller en dimensjon tom, får du ingen stopp, men verdien mangler i eksportfila. Fyll derfor ut alle feltene Økonomi har oppgitt, og sjekk listen under [Produkter](/produkter) før du genererer.

## Kan jeg endre senere?

Ja. Konteringen kan justeres ved behov, men en endring gjelder for kjøringer som genereres etterpå. En kjøring du allerede har generert, må du forkaste og generere på nytt for å få med endringen. Se [Forkast og generer på nytt](forkast-og-generer-pa-nytt.md).

## Slik ser du at det gikk bra

På listen under [Produkter](/produkter) står verdiene i kolonnene Konto, Dim1, Dim2, Dim4 og Artikkel-id i stedet for «—». Endringen er logget i [Hendelseslogg](/hendelseslogg).
