---
tittel: Ingen aktiv prisversjon
sammendrag: Hva du gjør når generering stopper fordi ingen aktiv prisversjon dekker perioden, eller flere gjør det.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /prisversjoner
apne-i-appen-tekst: Åpne Prisversjoner
relatert: [slik-gjor-du/lag-prisversjon, forsta-systemet/prisversjon-og-gyldighet, oppslag/statuser, manedsrutinen/1-sjekk-registrene]
nokkelord: [prisversjon, aktiv, gyldig fra, periode, overlapp]
---
## Hva du ser

Når du trykker [Generer](knapp:), får du siden «Kan ikke fullføre» med en av meldingene:

- «Ingen aktiv prisversjon dekker 2027-01»
- «Flere aktive prisversjoner dekker 2027-01»

Perioden i meldingen er den måneden du prøvde å generere.

## Hvorfor

En kjøring bruker den aktive prisversjonen som dekker **månedens første dag**. Gyldig fra og til er en regel, ikke bare informasjon. Begge datoene er med, og tom «til» betyr uten sluttdato.

Det betyr at:

- en versjon som er utkast eller arkivert, aldri brukes
- en versjon som er aktiv, men starter midt i måneden, dekker først neste måned
- to aktive versjoner kan ikke overlappe. Meldingen «Flere aktive …» skal derfor normalt ikke oppstå. Får du den, kontakt forvaltningen.

## Hva du gjør

1. Åpne [Prisversjoner](/prisversjoner) og se på statusen og datoene til versjonene.
2. Finnes det en utkastversjon for året, åpne den og sjekk at alle aktive produkter har pris. Trykk deretter [Aktiver](knapp:). Se [Lag en prisversjon](../slik-gjor-du/lag-prisversjon.md).
3. Er en versjon aktiv, men dekker ikke datoen, sjekk **gyldig fra**. Siden for versjonen sier hvilke perioder den brukes for.
4. Prøv [Generer](knapp:) igjen.

Er to versjoner aktive for samme periode, kontakt forvaltningen. Aktivering stopper med «Datointervallet overlapper med aktiv prisversjon» så lenge en annen aktiv versjon dekker samme datoer.

> [!TIP]
> Mangler perioden også bruksdata, får du meldingen om bruksdata først. Se [Ingen bruksdata](ingen-bruksdata.md).
