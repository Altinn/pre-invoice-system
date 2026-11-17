---
tittel: Roller og hva de kan gjøre
sammendrag: Oversikt over hva LESER, FORVALTER og GODKJENNER kan gjøre, side for side og handling for handling.
oppdatert: 2026-11-17
relatert: [kom-i-gang/roller-og-tilgang, nar-noe-gar-galt/ingen-tilgang, manedsrutinen/5-godkjenn]
nokkelord: [rolle, tilgang, rettigheter, leser, forvalter, godkjenner, hvem kan]
---
Rollen din styrer hva du får endre. Alle som er logget inn, kan lese alt. Det er bare handlinger som endrer noe, som krever en rolle.

## De tre rollene

| Rolle | Hovedoppgave |
|---|---|
| **LESER** | Se registre, bruksdata, kjøringer og logg, og laste ned filer. Kan ikke endre noe. |
| **FORVALTER** | Vedlikeholde registre, hente eller laste opp bruksdata, generere og forkaste kjøringer. |
| **GODKJENNER** | Godkjenne og eksportere kjøringer. |

> [!IMPORTANT]
> Rollene bygger ikke oppå hverandre. GODKJENNER får ikke FORVALTERs rettigheter automatisk, og FORVALTER kan ikke godkjenne. Roller tildeles gjennom grupper, og du har begge bare hvis du er med i begge gruppene. I den lokale demoen har brukeren alle tre.

## Handling for handling

| Du vil … | LESER | FORVALTER | GODKJENNER |
|---|---|---|---|
| Se alle sider, også [Hjelp](/hjelp) og [Hendelseslogg](/hendelseslogg) | ja | ja | ja |
| Laste ned filer og regneark (fakturagrunnlag, lister, loggen) | ja | ja | ja |
| Endre produkter, kontering og kildenavn | nei | ja | nei |
| Lage, prissette, aktivere og arkivere prisversjoner | nei | ja | nei |
| Endre terskelverdi (i et utkast) | nei | ja | nei |
| Endre kunder, fakturareferanser og kundenummerregler | nei | ja | nei |
| Hente fra datavarehus, laste opp fil og bekrefte import | nei | ja | nei |
| Generere en kjøring med [Generer](knapp:) | nei | ja | nei |
| Endre tekstene på fakturaen (mens kjøringen er GENERERT) | nei | ja | nei |
| Forkaste en kjøring | nei | ja | nei |
| Lage korrigeringskjøring fra en sammenligning | nei | ja | nei |
| Krysse av **Funn akseptert** på en advarsel | nei | ja | ja |
| [Godkjenn](knapp:) en kjøring | nei | nei | ja |
| [Eksporter](knapp:) en kjøring | nei | nei | ja |

Testverktøyet «nullstill periode» finnes bare i testmiljøet tt02 og lokalt. Det krever FORVALTER.

## Hva du ser når du mangler rolle

Knappene vises likevel. Prøver du en handling du ikke har rolle for, får du siden «Ingen tilgang» og ingenting blir endret. Se [Ingen tilgang](../nar-noe-gar-galt/ingen-tilgang.md).

Er du innlogget uten noen av de tre rollene, kan du fortsatt lese. Rollene følger gruppetilhørigheten din ved innlogging, og du kan ikke endre dem i forsystem selv.

## Fordeling i Månedsrutinen

- Steg 1–3 (registre, bruksdata, generering) er FORVALTERs arbeid.
- Gjennomgang av funn kan gjøres av FORVALTER eller GODKJENNER.
- Godkjenning og eksport er GODKJENNERs arbeid. Se [Godkjenn](../manedsrutinen/5-godkjenn.md).
