---
tittel: Gå gjennom kontrollfunn
sammendrag: Du retter blokkerende funn og krysser av for advarsler du har sett på, slik at kjøringen kan godkjennes.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [oppslag/kontrollfunn, slik-gjor-du/sjekk-ut-advarsler, slik-gjor-du/forkast-og-generer-pa-nytt, manedsrutinen/3-generer, manedsrutinen/5-godkjenn, nar-noe-gar-galt/godkjenn-er-gra]
nokkelord: [kontroll, blokkerende, advarsel, funn akseptert, sjekk ut]
---
Kontrollene kjøres automatisk når du genererer. Du må gå gjennom funnene før kjøringen kan godkjennes.

## Hvem

Både FORVALTER og GODKJENNER kan krysse av for advarsler. Bare FORVALTER kan forkaste og generere på nytt. LESER kan se funnene, men ikke krysse av.

## Før du starter

[Kjøringen er generert](3-generer.md). Åpne den og velg fanen [Kontroller](knapp:). Tallet ved fanen viser hvor mange blokkerende funn eller åpne advarsler som gjenstår.

## Gjør slik

1. Les tabellen under **Kontrollfunn**. Hver rad er én kode med alvorlighet, antall og et eksempel på meldingen. Hva kodene betyr og hvordan du løser dem, står i [Kontrollfunn](../oppslag/kontrollfunn.md).
2. **Blokkerende funn** må rettes. De kan ikke krysses av. Rett registeret eller bruksdataene, forkast kjøringen med [Forkast](knapp:), og generer på nytt. Se [Forkast og generer på nytt](../slik-gjor-du/forkast-og-generer-pa-nytt.md).
3. **Advarsler** trenger ikke rettes, men du må se på dem. Hvordan, står i [Sjekk ut advarsler](../slik-gjor-du/sjekk-ut-advarsler.md). Er advarselen i orden, kryss av for [Funn akseptert](knapp:) på raden. Har flere funn samme kode, sjekker [Funn akseptert (alle N)](knapp:) ut alle på én gang. Under **Vis alle** kan du krysse av hver for seg.
4. Fjernet du et kryss, er advarselen åpen igjen.

> [!TIP]
> Fakturaer uten fakturareferanse og kunder under terskelverdien har egne lister, på fanen Kontroller og på fanen Fakturaer. Begge kan lastes ned som XLSX eller CSV.

## Slik ser du at det gikk bra

Oversikten sier «Ingen blokkerende funn», og «N advarsler, gjennomgått og sjekket ut. Hindrer ikke godkjenning». Under hvert kryss står hvem som krysset av, og når. Hver avkrysning står også i kjøringens Historikk. Fortsett til [Godkjenn](5-godkjenn.md).

## Hvis noe går galt

- **Knappen Godkjenn er grå.** Det gjenstår blokkerende funn eller advarsler som ikke er sjekket ut. Se [Godkjenn er grå](../nar-noe-gar-galt/godkjenn-er-gra.md).
- **Du kan ikke krysse av.** Du har rollen LESER, eller kjøringen er allerede godkjent. Avkrysningene låses ved godkjenning.
- **Du forkastet og genererte på nytt, og avkrysningene er borte.** Det er riktig. En ny kjøring starter uten avkrysninger.
- **Du er usikker på en advarsel.** La den stå åpen og spør Økonomi eller kunden. Å krysse av betyr at du har sett på den.
