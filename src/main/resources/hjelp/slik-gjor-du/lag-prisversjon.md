---
tittel: Lag en ny prisversjon
sammendrag: Slik oppretter du en ny prisversjon som utkast, setter enhetspris per produkt og aktiverer den.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /prisversjoner
apne-i-appen-tekst: Åpne Prisversjoner
relatert: [slik-gjor-du/endre-pris-eller-terskelverdi, forsta-systemet/prisversjon-og-gyldighet, nar-noe-gar-galt/ingen-aktiv-prisversjon, forsta-systemet/terskelverdi]
nokkelord: [prisversjon, ny prisversjon, prisliste, enhetspris, aktiver, arkiver, utkast, gyldig fra]
---
En prisversjon er en prisliste med gyldighetsperiode, vanligvis ett år. Alle kjøringer bruker enhetsprisene fra den aktive prisversjonen som dekker perioden. Du trenger rollen FORVALTER for å lage og aktivere en versjon.

## Gjør slik

1. Åpne [Prisversjoner](/prisversjoner).
2. Under «Ny prisversjon (utkast)» fyller du ut **Navn** og **Gyldig fra**. **Gyldig til** er valgfritt. Tom «til» betyr at versjonen ikke har sluttdato.
3. Trykk [Opprett](knapp:). Du kommer rett til den nye versjonen, som har status UTKAST.
4. Skriv inn **Enhetspris** (eksl. mva) for hvert produkt i tabellen. Trykk [Lagre](knapp:) på hver rad.
5. Sjekk **Terskelverdi** øverst. Se [Endre pris eller terskelverdi](endre-pris-eller-terskelverdi.md) hvis den skal være noe annet enn standarden.
6. Trykk [Aktiver](knapp:), og bekreft med [Ja, aktiver](knapp:).

## Hva datoene betyr

Datoene er en regel, ikke bare informasjon. En kjøring bruker den aktive versjonen som dekker **periodens første dag**. Begge datoer er med.

- En versjon som starter 15. januar, brukes først fra februar. Januar dekkes ikke.
- Siden viser i klartekst hvilke perioder versjonen brukes for, for eksempel «Brukes for kjøringer fra januar 2027 og fremover».

> [!WARNING]
> Prisene kan ikke endres etter aktivering. Sjekk dem før du trykker [Aktiver](knapp:). Skal en pris endres senere, må du lage en ny prisversjon.

## Før systemet lar deg aktivere

Aktiveringen stopper med en melding hvis

- et aktivt produkt mangler enhetspris, eller
- datointervallet overlapper en annen aktiv versjon.

Rett opp det meldingen peker på, og prøv igjen. Et inaktivt produkt trenger ikke pris. Det vises bare i tabellen hvis versjonen allerede har en pris for det.

## Slik ser du at det gikk bra

Versjonen har status AKTIV, og overskriften sier hvilke perioder den brukes for. Prisene står låst, og knappen [Arkiver](knapp:) er kommet i stedet for [Aktiver](knapp:).

## Arkivere en versjon

Når en versjon ikke skal brukes lenger, trykker du [Arkiver](knapp:) og bekrefter. En arkivert versjon brukes ikke av nye kjøringer og kan ikke gjenåpnes. Har den gamle versjonen ingen sluttdato, må den arkiveres før en ny versjon med overlappende datoer kan aktiveres. Sørg for at den nye versjonen er klar med priser, så du ikke står uten aktiv prisversjon lenge.
