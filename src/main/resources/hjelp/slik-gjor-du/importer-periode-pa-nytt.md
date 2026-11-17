---
tittel: Importer en periode på nytt
sammendrag: Slik erstatter du bruksdata for en måned som allerede er importert, og hva du må gjøre med en eksisterende kjøring først.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/last-opp-csv, slik-gjor-du/forkast-og-generer-pa-nytt, nar-noe-gar-galt/import-blokkeres, nar-noe-gar-galt/eksportert-kan-ikke-forkastes, slik-gjor-du/lag-korrigeringskjoring]
nokkelord: [reimport, erstatte, AVVIST, blokkert, rette bruksdata]
---
Oppdager du feil i bruksdataene til en måned, kan du laste opp perioden på nytt. Den nye importen **erstatter** den gamle. Du trenger rollen FORVALTER.

## Hva som skjer

- De gamle radene slettes ikke. Den forrige importen får status AVVIST og er ikke lenger grunnlaget for perioden. Radene blir bevart for sporbarheten.
- Den nye importen blir grunnlaget når du bekrefter den.
- Hendelsen står i [Hendelseslogg](/hendelseslogg).

## Hvis det finnes en kjøring for perioden

Forsystem blokkerer ny import så lenge det finnes en kjøring for perioden som ikke er forkastet. Meldingen i forhåndsvisningen er «Det finnes allerede en fakturakjøring for … som ikke er forkastet. Forkast den før ny import.» Det hindrer at du endrer bruk en kjøring allerede bygger på.

### Gjør slik

1. Åpne kjøringen for perioden i [Kjøringer](/kjoringer) og forkast den. Se [Forkast og generer på nytt](forkast-og-generer-pa-nytt.md).
2. Gå til [Bruksdata](/bruksdata) og last opp den rettede fila som beskrevet i [Last opp bruksdata fra en fil](last-opp-csv.md), eller hent perioden på nytt fra datavarehuset.
3. Les forhåndsvisningen og trykk [Bekreft import](knapp:).
4. Generer kjøringen på nytt.

> [!WARNING]
> En kjøring som er eksportert kan ikke forkastes. Da må perioden rettes med en korrigeringskjøring i stedet. Se [Eksportert kan ikke forkastes](../nar-noe-gar-galt/eksportert-kan-ikke-forkastes.md) og [Lag en korrigeringskjøring](lag-korrigeringskjoring.md). Laster du opp en datavarehusfil for en periode som er eksportert, viser forsystem en sammenligning med det som ble eksportert, ikke en vanlig import.

## Slik ser du at det gikk bra

I tabellen under «Importer» står den nye importen med status VALIDERT, og den forrige med AVVIST. Den nye kjøringen bygger på den nye importen.

## Hvis noe går galt

Kommer du ikke videre fordi importen er blokkert, se [Import blokkeres](../nar-noe-gar-galt/import-blokkeres.md).
