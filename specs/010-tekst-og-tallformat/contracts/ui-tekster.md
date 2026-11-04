# Contract: user-facing wording (specs/010)

Wording quoted from Økonomi is verbatim. The rest is ours.

| Where | Text |
|---|---|
| Bruksdata preview, before «Bekreft import» | Ved å trykke på Bekreft import importeres data fra datavarehuset. Importen kan ikke slettes eller endres etter at importen er bekreftet. |
| Bruksdata, before «Hent fra datavarehus» | Ved å trykke på Hent fra datavarehus hentes bruksdata for perioden og vises for kontroll. Når importen er bekreftet, kan den ikke slettes eller endres. |
| Bruksdata, after the import list | Neste steg: når bruksdata for perioden er importert, går du til **Kjøringer** og genererer fakturagrunnlaget. |
| Kjøringer, top | Bruksdata for perioden må være importert eller hentet fra datavarehuset (under **Bruksdata**) før du kan generere en fakturakjøring. |
| Kontroller, findings column | Beskrivelse (was Eksempel) |
| Kontroller, severity explanation | Blokkerende: må rettes før kjøringen kan godkjennes. Advarsel: bør sjekkes, men hindrer ikke godkjenning. |
| Avstemming bruksdata, intro | Denne avstemmingen brukes for å kontrollere at alle bruksdata har kommet med på fakturagrunnlaget. Her hensyntas ikke prisliste eller kostnad som skal faktureres. |
| Avstemming bruksdata, columns | Importerte rader fra datavarehus · Importert bruksdata fra datavarehus · Fakturerte linjer · Fakturert bruksdata |
| Produkter, dimension headers | Dim1 Koststed · Dim2 Prosjekt/formål · Dim4 Kappost |
| Amount labels | «… (eksl. mva)» or «… eksl. mva» on every amount column and sum |

## Number format

| Kind | Example | Rule |
|---|---|---|
| Amount | 1 234 567,89 | Space thousands, comma, 2 decimals, half up |
| Count | 1 234 567 | Space thousands, no decimals |
| Usage quantity | 1 234,00 | As amount |
| Unit price | 0,0375 · 1 250,00 | Space thousands, comma, all stored decimals, at least 2 |
| Missing | — | null |
