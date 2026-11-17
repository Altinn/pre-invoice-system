---
tittel: Godkjenn er grå
sammendrag: Hvorfor Godkjenn-knappen ikke kan trykkes, og hva du gjør med blokkerende funn og advarsler.
oppdatert: 2026-11-17
rolle: GODKJENNER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/4-kontrollfunn, manedsrutinen/5-godkjenn, oppslag/kontrollfunn, slik-gjor-du/sjekk-ut-advarsler, slik-gjor-du/forkast-og-generer-pa-nytt]
nokkelord: [godkjenn, deaktivert, grå knapp, blokkerende, advarsel]
---
## Hva du ser

På kjøringens Oversikt er [Godkjenn](knapp:) grå. Under knappen står en av disse linjene:

- «Godkjenning er låst til de blokkerende funnene er løst (forkast, rett data, generer på nytt).»
- «Godkjenning er låst til alle advarsler er sjekket ut på fanen Kontroller.»

## Hvorfor

Knappen kan bare brukes når to ting er i orden:

- Kjøringen har **ingen blokkerende kontrollfunn**.
- Alle **advarsler er sjekket ut**. Det vil si at noen har krysset av «Funn akseptert».

## Hva du gjør

1. Åpne fanen [Kontroller](knapp:) på kjøringen. Et tall på fanen viser hvor mange blokkerende funn eller advarsler som gjenstår.
2. **Blokkerende funn** (rødt) kan ikke godtas. Les hva som er galt i [Kontrollfunn](../oppslag/kontrollfunn.md), rett registeret eller bruksdata, forkast kjøringen og generer på nytt. Se [Forkast og generer på nytt](../slik-gjor-du/forkast-og-generer-pa-nytt.md).
3. **Advarsler** (gule) trenger ikke rettes, men noen må se på dem. Kryss av «Funn akseptert» når du har sett på dem. Se [Sjekk ut advarsler](../slik-gjor-du/sjekk-ut-advarsler.md). Du trenger rollen FORVALTER eller GODKJENNER.
4. Gå tilbake til Oversikt. Når alt er ryddet, er [Godkjenn](knapp:) aktiv.

> [!TIP]
> Mangler du rollen GODKJENNER, ser du knappen, men får «Ingen tilgang» når du bruker den. Se [Ingen tilgang](ingen-tilgang.md).

Prøver du å godkjenne likevel, for eksempel fra en gammel side, får du en av meldingene «Kjøringen har blokkerende kontrollfunn og kan ikke godkjennes» eller «… er ikke sjekket ut. Kryss av «Funn akseptert» på fanen Kontroller før kjøringen godkjennes.» Ingenting blir godkjent.
