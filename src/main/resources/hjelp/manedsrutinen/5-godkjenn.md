---
tittel: Godkjenn
sammendrag: Du godkjenner kjøringen når det ikke er blokkerende funn og alle advarsler er sjekket ut.
oppdatert: 2026-11-17
rolle: GODKJENNER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/4-kontrollfunn, manedsrutinen/6-eksporter, nar-noe-gar-galt/godkjenn-er-gra, slik-gjor-du/forkast-og-generer-pa-nytt, oppslag/roller]
nokkelord: [godkjenne, godkjent, klar til eksport]
---
Godkjenning er det siste du gjør før eksport. Etter godkjenning kan grunnlaget eksporteres til regnskap.

## Hvem

Du trenger rollen GODKJENNER. FORVALTER kan ikke godkjenne, med mindre du også har GODKJENNER. Se [Roller](../oppslag/roller.md).

**Sidemannskontroll:** Den som genererte kjøringen, kan ikke godkjenne den selv. En annen person med rollen GODKJENNER må gjøre det.

## Før du starter

- Det er ingen blokkerende funn.
- Alle advarsler er sjekket ut. Se [Gå gjennom kontrollfunn](4-kontrollfunn.md).
- Du har sett over Sammendrag og tekstene på fakturaen. De låses nå.

## Gjør slik

1. Åpne kjøringen under [Kjøringer](/kjoringer). Den har status «Venter på godkjenning».
2. På fanen [Oversikt](knapp:) viser sjekklisten «Neste steg: kontroller og godkjenn» hva som gjenstår.
3. Trykk [Godkjenn](knapp:).
4. Bekreft i dialogen «Godkjenne kjøringen for ...?» med [Ja, godkjenn](knapp:). Etter godkjenning kan kjøringen fortsatt forkastes av en FORVALTER, men ikke etter eksport.

## Slik ser du at det gikk bra

Kjøringen har status «Klar til eksport», og siden viser «Godkjent av» med navn og tidspunkt, og «Neste steg: eksporter til regnskap». Fortsett til [Eksporter og send videre](6-eksporter.md).

## Hvis noe går galt

- **Godkjenn er grå.** Siden sier hvorfor: blokkerende funn, advarsler som ikke er sjekket ut, eller at du genererte kjøringen selv. Se [Godkjenn er grå](../nar-noe-gar-galt/godkjenn-er-gra.md).
- **Du ser ikke Godkjenn, eller får «Ingen tilgang».** Du mangler rollen GODKJENNER.
- **Du oppdager en feil etter godkjenning.** Be en FORVALTER trykke [Forkast](knapp:), rette feilen og generere på nytt. Er kjøringen eksportert, kan den ikke forkastes. Se [Eksportert kan ikke forkastes](../nar-noe-gar-galt/eksportert-kan-ikke-forkastes.md).
