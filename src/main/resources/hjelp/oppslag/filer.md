---
tittel: Filene forsystem lager
sammendrag: Hva LG04-filen, PDF-zip-en, CSV og XLSX er, hvor du laster dem ned og hvem de vanligvis går til.
oppdatert: 2026-11-17
relatert: [manedsrutinen/6-eksporter, slik-gjor-du/last-ned-fakturagrunnlag, slik-gjor-du/kopier-epost-til-regnskap, oppslag/csv-format, slik-gjor-du/er-lg04-lastet-ned]
nokkelord: [lg04, pdf, zip, csv, xlsx, eksportfil, nedlasting, regneark, unit4, sha-256]
---
Når en kjøring eksporteres, lager forsystem fire filer og arkiverer dem. Dette er oversikten over hva de er og hvem som typisk får dem.

## Filene som lages ved eksport

| Fil | Hva er det | Hvem får den vanligvis |
|---|---|---|
| **LG04** | Tekstfil med fast bredde (Windows-1252) som Unit4 leser inn. Én ordre per faktura. | Regnskap hos VIS, som leser den inn i Unit4. |
| **PDF** (zip) | Én detaljvisning per faktura, samlet i en zip-fil. | Tjenesteeierne, som får detaljvisningen til fakturaen. |
| **CSV** | Alle fakturalinjer, kommaseparert. | Til kontroll og videre bruk i andre verktøy. |
| **XLSX** | Alle fakturalinjer som Excel-regneark, med samme kolonner som nedlastingen før godkjenning. | Til analyse og kontroll. |

Filnavnene inneholder perioden, for eksempel `lg04-2027-01.txt` og `pdf-2027-01.zip`.

### Om LG04

Filen er kodet i Windows-1252 allerede når du laster den ned. Du trenger ikke konvertere den. LG04-filen er den eneste som går inn i Unit4. Den leses tilbake og sammenlignes med kjøringen før noe lagres, se [Eksport stoppet](../nar-noe-gar-galt/eksport-stoppet.md).

### Om PDF-fila

Detaljvisningene sendes ikke ut av forsystem. Fordelingen til tjenesteeierne gjøres manuelt, slik det er avtalt. Hvordan PDF-ene skal oppbevares over tid, er ikke avklart (OQ-9).

## Hvor du laster ned

Åpne kjøringen og fanen [Filer](knapp:).

- **Etter eksport** står alle fire filene i tabellen **Eksportfiler**, med type, filnavn og sjekksum (SHA-256). Trykk [Last ned](knapp:) på raden.
- **Før eksport** finnes fakturagrunnlaget som regneark som lenkene **Fakturagrunnlag (XLSX, for Excel)** og **Fakturagrunnlag (CSV)**. Filnavnet inneholder statusen, så et utkast ikke forveksles med det godkjente grunnlaget. For en forkastet kjøring kan grunnlaget ikke lastes ned.

Andre nedlastinger finner du der de hører hjemme. Lister fra fanene Kontroller og Fakturaer har egne lenker for XLSX og CSV. Hendelsesloggen kan lastes ned fra [Hendelseslogg](/hendelseslogg). Se [Last ned fakturagrunnlaget](../slik-gjor-du/last-ned-fakturagrunnlag.md).

## Hvem kan laste ned

Alle innloggede kan laste ned filer. Å eksportere, altså lage filene, krever GODKJENNER. Se [Roller](roller.md).

> [!TIP]
> Hver nedlasting logges. I [Hendelseslogg](/hendelseslogg) kan du se om LG04-filen allerede er hentet. Se [Er LG04 lastet ned?](../slik-gjor-du/er-lg04-lastet-ned.md).

## Videre til regnskap

Forsystem sender ikke filene selv. En person sender LG04-filen med e-post til regnskap, med periode, antall fakturaer og samlet beløp i e-posten. Tallene står klare på kjøringen. Se [Kopier e-post til regnskap](../slik-gjor-du/kopier-epost-til-regnskap.md).

CSV-formatet for filene du laster *inn*, er et annet. Det står under [CSV-format](csv-format.md).
