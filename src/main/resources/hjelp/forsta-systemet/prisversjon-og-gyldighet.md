---
tittel: Prisversjon og gyldighet
sammendrag: Hvorfor priser samles i versjoner, hvordan gyldighetsdatoene styrer hvilken versjon en kjøring bruker, og hvorfor aktive versjoner er låst.
oppdatert: 2026-11-17
relatert: [slik-gjor-du/lag-prisversjon, slik-gjor-du/endre-pris-eller-terskelverdi, nar-noe-gar-galt/ingen-aktiv-prisversjon, forsta-systemet/terskelverdi, oppslag/statuser]
nokkelord: [prisliste, gyldig fra, gyldig til, aktiv, arkivert, utkast, enhetspris, overlapp]
---
Prisene ligger ikke løst på produktene. De ligger i en **prisversjon**, en prisliste med gyldighetsperiode. Det gjør at en kjøring alltid kan forklares med hvilken prisliste den brukte.

## Hvorfor versjoner

Priser fastsettes en gang i året. For 2027 er prislisten allerede publisert og kan legges inn i forsystem. Forsystem er der produktene og prisene vedlikeholdes, siden det ikke finnes noe annet produktregister. Samles prisene i en versjon, blir spørsmålet «hva kostet det i januar?» et spørsmål om hvilken versjon som gjaldt, ikke om hva noen husker.

Hver fakturalinje peker på prisen den ble beregnet med. Se [Sporbarhet](sporbarhet.md).

## Tre statuser

| Status | Betyr | Kan endres? |
|---|---|---|
| **UTKAST** | Under arbeid. Brukes ikke av kjøringer. | Ja, pris og terskelverdi. |
| **AKTIV** | Brukes når kjøringer genereres. | Nei, alt er låst. |
| **ARKIVERT** | Pensjonert. Brukes ikke av nye kjøringer. | Nei, og den kan ikke gjenåpnes. |

Se [Statuser](../oppslag/statuser.md) for alle statuser i systemet.

## Hvordan datoene virker

**Gyldig fra** og **Gyldig til** er en regel, ikke bare informasjon.

- En kjøring for en periode bruker den aktive versjonen som dekker **periodens første dag**.
- Begge datoene er med. Tom «til» betyr uten sluttdato.
- To aktive versjoner kan ikke overlappe.

Eksempel med oppdiktede datoer. En versjon er gyldig fra 15. januar. Kjøringen for januar sjekker 1. januar, som ikke er dekket. Versjonen brukes først for februar, for 1. februar er etter 15. januar. Skal januar prises med den, må «Gyldig fra» være 1. januar eller tidligere.

Perioden er alltid en hel måned og velges alltid eksplisitt. Én fast dato gir ett entydig svar, så prisen bytter per måned og aldri midt i en måned.

Siden til hver versjon sier i klartekst hvilke perioder den brukes for, for eksempel «Brukes for kjøringer fra januar 2027 og fremover».

## Hva som må være på plass for å aktivere

Aktiveringen stopper med en melding hvis

- et aktivt produkt mangler enhetspris, eller
- datointervallet overlapper en annen aktiv versjon.

Et inaktivt produkt trenger ikke pris.

## Hvorfor aktive versjoner er låst

Hadde en pris kunne endres etter at kjøringer ble laget, ville ikke lenger fakturaene stemme med prislisten de ble laget fra. Derfor må en endring skje i en ny versjon. Terskelverdien følger samme regel, siden den også hører til prisversjonen. Se [Terskelverdi](terskelverdi.md).

En kjøring som allerede er generert, bruker prisene den ble generert med. Skal den bruke nye priser, må den forkastes og genereres på nytt.

## Hva som skjer uten dekkende versjon

Finnes ingen aktiv versjon som dekker perioden, kan du ikke generere. Meldingen er «Ingen aktiv prisversjon dekker …». Se [Ingen aktiv prisversjon](../nar-noe-gar-galt/ingen-aktiv-prisversjon.md).

Hvis bare ett produkt mangler pris i versjonen, får kjøringen et blokkerende funn `MANGLER_PRIS` for det produktet.

## Hva som er åpent

Artiklene her oppgir ingen priser. Prisene hører hjemme i prisversjonen i appen. Kontering ligger på produktet, ikke i prisversjonen, og Økonomi har sagt at FinMod ikke har påslag på pris (OQ-2). Hvordan rettelser og avregning for tidligere perioder skal fungere, er ikke avklart (OQ-6).

Se [Lag en ny prisversjon](../slik-gjor-du/lag-prisversjon.md) og [Endre en pris eller terskelverdien](../slik-gjor-du/endre-pris-eller-terskelverdi.md) for fremgangsmåten.
