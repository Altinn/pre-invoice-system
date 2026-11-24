---
tittel: Kontrollfunn
sammendrag: Her ser du hva Blokkerende og Advarsel betyr, hvordan du sjekker ut advarsler, og hva hver kontrollkode betyr og hvordan du løser den.
oppdatert: 2026-11-17
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/4-kontrollfunn, slik-gjor-du/sjekk-ut-advarsler, slik-gjor-du/forkast-og-generer-pa-nytt, nar-noe-gar-galt/godkjenn-er-gra]
nokkelord: [kontrollkode, blokkerende, advarsel, funn akseptert, MANGLER_KUNDENUMMER, MOTSTRIDENDE_KUNDEDATA, MANGLER_PRIS, MANGLER_KONTERING, terskel]
---
Kontrollene kjøres automatisk når du genererer en kjøring. Du ser funnene på fanen [Kontroller](knapp:) i kjøringen.

## Blokkerende og Advarsel

Hvert funn har en alvorlighet.

| Alvorlighet | Betyr | Hva du gjør |
|---|---|---|
| **Blokkerende** | Kjøringen kan ikke godkjennes. | Rett registeret eller bruksdataene, forkast kjøringen, og generer på nytt. Blokkerende funn kan ikke krysses av. |
| **Advarsel** | Funnet trenger ikke rettes, men noen må ha sett på det. | Kryss av for [Funn akseptert](knapp:) når du har sjekket det. |

Kjøringen kan ikke godkjennes før alle advarsler er sjekket ut.

## Slik sjekker du ut advarsler

1. Åpne kjøringen og velg fanen Kontroller.
2. Se på advarselen og vurder om den er i orden. Hva du bør sjekke for hver kode, står i oversikten nedenfor.
3. Kryss av for **Funn akseptert** på raden. Har flere funn samme kode, sjekker **Funn akseptert (alle N)** ut alle på én gang.
4. Raden viser hvem som krysset av, og når. Fjerner du krysset, er advarselen åpen igjen.

FORVALTER og GODKJENNER kan krysse av. LESER kan ikke. Avkrysningene låses når kjøringen godkjennes, og en kjøring som genereres på nytt, starter uten avkrysninger. Se også [Sjekk ut advarsler](../slik-gjor-du/sjekk-ut-advarsler.md).

## Alle kontrollkodene

Oversikten under hentes direkte fra appen, så den viser alltid de kodene som finnes.

{{kontrollkoder}}
