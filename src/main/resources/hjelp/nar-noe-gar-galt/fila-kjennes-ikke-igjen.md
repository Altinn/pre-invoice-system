---
tittel: Fila kjennes ikke igjen
sammendrag: Hva filfeilene ved opplasting av bruksdata betyr, og hvordan du får en fil forsystem kan lese.
oppdatert: 2026-11-24
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/last-opp-csv, oppslag/csv-format, manedsrutinen/2-hent-bruksdata]
nokkelord: [csv, semikolon, excel, format, kolonner, opplasting, fil, UTF-8, ANSI, tegnsett, æøå, Ã¸]
---
## Hva du ser

Når du trykker [Forhåndsvis](knapp:), får du siden «Kan ikke fullføre» med en av meldingene:

- «Ingen fil ble lastet opp»
- «Fila er tom»
- «Fila er et Excel-regneark, ikke CSV. Last opp den opprinnelige CSV-fila.»
- «Fila er delt med semikolon, ikke komma. Det skjer når den er åpnet og lagret i Excel. Last opp den opprinnelige fila.»
- «Kjenner ikke igjen formatet på fila.» Meldingen lister kolonnene som ble funnet, og de som ble forventet.
- «Fila er ikke lagret som UTF-8 (linje N), så æ, ø og å ville blitt feil.»
- «Fila har æ, ø eller å som er konvertert feil («Ã¸», linje N).»

## Hvorfor

forsystem gjenkjenner fila ved å lese kolonneoverskriftene på første linje. Den godtar to formater: forsystemets eget CSV-format og eksporten fra datavarehuset. Hvis fila er endret underveis, passer den ikke lenger.

Den vanligste årsaken er at fila er åpnet og lagret på nytt i Excel. Da blir den enten et regneark, eller en CSV med semikolon i stedet for komma.

Begge formatene skal være i tegnsettet UTF-8. Excel åpner og lagrer ofte CSV som «ANSI» (Windows-1252). Da får du en av to feil:

- Fila er lagret som ANSI. Forsystemet kan ikke lese æ, ø og å i den.
- Fila er åpnet som ANSI og lagret som UTF-8. Da er «ø» allerede blitt til «Ã¸», og en referanse på 25 tegn ser ut til å ha 26.

Forsystemet avviser fila i stedet for å sette feil navn eller referanse på fakturaen.

## Hva du gjør

1. Last opp fila **slik du fikk den**, uten å åpne og lagre den i Excel først. Må du bruke Excel, velger du filopprinnelse «65001: Unicode (UTF-8)» når du åpner fila, og lagrer som «CSV UTF-8».
2. Sammenlign kolonnene i meldingen med [CSV-formatet](../oppslag/csv-format.md). Nøyaktig stavemåte og kolonnerekkefølge betyr noe.
3. Mangler du en riktig fil, be om en ny fra datavarehuset, eller hent bruken direkte fra datavarehuset hvis det er slått på. Se [Hent bruksdata](../manedsrutinen/2-hent-bruksdata.md).
4. Prøv [Forhåndsvis](knapp:) igjen.

> [!IMPORTANT]
> Fila må være feilfri for å kunne importeres. Er det avviste rader i forhåndsvisningen, importeres ingenting. Rett fila og last opp på nytt. Se [Last opp en CSV-fil](../slik-gjor-du/last-opp-csv.md).
