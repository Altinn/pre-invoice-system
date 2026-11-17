---
tittel: Import blokkeres av en kjøring
sammendrag: Hvorfor en import ikke kan bekreftes når perioden allerede har en kjøring, og hvordan du går videre.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [slik-gjor-du/importer-periode-pa-nytt, slik-gjor-du/forkast-og-generer-pa-nytt, nar-noe-gar-galt/eksportert-kan-ikke-forkastes]
nokkelord: [import, blokkert, ny import, forkast, kjøring finnes]
---
## Hva du ser

I forhåndsvisningen av bruksdata står det en blokkering: «Det finnes allerede en fakturakjøring for 2027-01 som ikke er forkastet. Forkast den før ny import.» Det er ingen [Bekreft import](knapp:), og ingenting blir lagret.

## Hvorfor

Bruken kan ikke endres etter at en kjøring er bygget på den. Derfor sperrer forsystem import for en periode så lenge det finnes en kjøring som ikke er forkastet. Det gjelder kjøringer som venter på godkjenning, godkjente og eksporterte.

## Hva du gjør

1. Åpne [Kjøringer](/kjoringer) og finn kjøringen for perioden.
2. Står den som «Venter på godkjenning» eller «Klar til eksport», åpne den og trykk [Forkast](knapp:). Du trenger rollen FORVALTER. Se [Forkast og generer på nytt](../slik-gjor-du/forkast-og-generer-pa-nytt.md).
3. Gå tilbake til [Bruksdata](/bruksdata) og last opp eller hent bruken på nytt. Se [Importer en periode på nytt](../slik-gjor-du/importer-periode-pa-nytt.md).
4. Generer kjøringen igjen.

Er kjøringen **eksportert**, kan den ikke forkastes. Da må du ikke importere på nytt. Se [Eksportert kan ikke forkastes](eksportert-kan-ikke-forkastes.md).

> [!TIP]
> Det er en bevisst sperre, ikke en feil. Du unngår at en kjøring og bruksdataene den bygger på kommer ut av takt.
