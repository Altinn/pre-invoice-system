---
tittel: CSV-format for bruksdata
sammendrag: Kolonnene, reglene og feilmeldingene for CSV-filene du laster opp under Bruksdata.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/last-opp-csv, slik-gjor-du/importer-periode-pa-nytt, nar-noe-gar-galt/fila-kjennes-ikke-igjen, nar-noe-gar-galt/import-blokkeres, slik-gjor-du/opprett-kunder-fra-fila]
nokkelord: [csv, kolonner, opplasting, avviste rader, semikolon, excel, datavarehus, eksport]
---
Under [Bruksdata](/bruksdata) tar ett opplastingsfelt imot to slags filer. Forsystem kjenner dem igjen på overskriftsraden. Her står det som gjelder for hvert format.

## Slik kjenner forsystem igjen fila

| Overskriftsraden har kolonnen | Formatet er |
|---|---|
| `organisasjonsnummer` | Forsystems eget CSV-format (under) |
| `organisation_number` | Eksport fra datavarehuset |
| ingen av dem | Fila avvises, og meldingen viser kolonnene fila faktisk har |

Opplastingen gjør ingenting før du har sett forhåndsvisningen og trykt [Bekreft import](knapp:). Se [Last opp en CSV-fil](../slik-gjor-du/last-opp-csv.md).

## Forsystems eget format

- Tegnsett UTF-8. En BOM først i fila er greit.
- Skilletegn komma. Desimaltegn punktum. Ingen tusenskille.
- Én overskriftsrad. Kolonnene gjenkjennes på navn, store eller små bokstaver, så rekkefølgen er likegyldig. Ekstra kolonner blir ignorert.
- Én måling per rad. Alle seks kolonnene må finnes i overskriften, også de som er tomme.

| Kolonne | Krav | Betydning |
|---|---|---|
| `periode` | alltid | Første dag i måneden, `ÅÅÅÅ-MM-01`. Må være lik perioden du velger ved opplasting. |
| `organisasjonsnummer` | alltid | Den som genererte bruken. Nøyaktig 9 siffer. |
| `produktkode` | alltid | Koden til et registrert produkt, slik den står i kolonnen **Kode** på [Produkter](/produkter). Skriv den akkurat slik. |
| `type` | alltid | `BRUKSVOLUM`, `AZURE_KOSTNAD` eller `SMS_KOSTNAD`. |
| `antall` | for `BRUKSVOLUM` | Volum, tall som ikke er negativt. Tom for kostnadsrader. |
| `belop` | for kostnadstypene | Beløp i kroner, ikke negativt. Tom for `BRUKSVOLUM`. |

Eksempel med oppdiktede organisasjonsnumre og tall:

```csv
periode,organisasjonsnummer,produktkode,type,antall,belop
2027-01-01,111111111,melding,BRUKSVOLUM,1500,
2027-01-01,111111111,varsling,SMS_KOSTNAD,,100.50
```

> [!IMPORTANT]
> Typene `AZURE_KOSTNAD` og `SMS_KOSTNAD` og betydningen av `belop` er foreløpige. Hvem som eier SMS-kostnader og hvordan de leveres, er ikke avklart (OQ-3), og Azure-kostnader følger det som avtales med datavarehuset (OQ-4). `BRUKSVOLUM` er stabil.

### Slik kontrolleres hver rad

Forhåndsvisningen viser hver avviste rad med linjenummer i fila og årsak.

| Årsak i forhåndsvisningen | Betyr |
|---|---|
| feil periode | `periode` er ikke første dag i perioden du valgte, eller kan ikke leses som dato |
| ugyldig organisasjonsnummer | Ikke nøyaktig 9 siffer |
| ukjent produktkode | Koden finnes ikke blant produktene |
| ugyldig type | `type` er ikke en av de tre |
| mangler, ugyldig eller negativ `antall` eller beløp | Verdien som typen krever, mangler, er ikke et tall eller er under 0 |
| duplikat | To rader har samme periode, organisasjonsnummer, produkt og type |

> [!WARNING]
> Alt eller ingenting. Har fila én avvist rad, kan den ikke importeres. Ingenting lagres før hele fila er gyldig. Rett fila og last opp på nytt.

### Linjene er enkle

Hver linje deles på komma uten å ta hensyn til anførselstegn. Verdier med komma i seg støttes ikke i dette formatet.

## Eksport fra datavarehuset

Brukes når API-et ikke kan nås. Fila går gjennom samme kontroll som en henting fra datavarehuset.

- Påkrevde kolonner er `organisation_number`, `product_name`, `quantity` og `amount_nok`, pluss én datokolonne. Datokolonnen kan hete `maaned`, `transaction_month` eller `transaction_date`.
- Andre kolonner, for eksempel navn, kundenummer og fakturareferanse, kan være med. Rekkefølgen er likegyldig.
- Produktnavnene må være koblet til et produkt under [Produkter → Kildenavn](/produkter/kildenavn). Et ukjent navn avviser importen.
- Organisasjoner i fila som ikke er kunder ennå, kan opprettes fra forhåndsvisningen. Se [Opprett kunder fra fila](../slik-gjor-du/opprett-kunder-fra-fila.md).
- Kolonnenavnene er ikke en avtalt grensesnittkontrakt med datavarehuset ennå (OQ-4), så de kan endre seg.

## Vanlige feil med fila

- **Fila er lagret i Excel.** Excel deler med semikolon, og da kjennes ikke fila igjen. Et ekte Excel-regneark (xlsx) avvises med egen melding. Last opp den opprinnelige fila slik du fikk den. Se [Fila kjennes ikke igjen](../nar-noe-gar-galt/fila-kjennes-ikke-igjen.md).
- **Perioden stemmer ikke.** Perioden du velger ved opplasting må være lik `periode` i alle rader.
- **Import blokkeres.** Finnes det allerede en kjøring for perioden som ikke er forkastet, kan du ikke importere. Se [Import blokkeres](../nar-noe-gar-galt/import-blokkeres.md).

## Laste opp samme periode på nytt

Når du bekrefter en ny import for en periode, erstatter den den forrige. De gamle radene tas ut av bruk, og den forrige importen får status AVVIST. Se [Importer en periode på nytt](../slik-gjor-du/importer-periode-pa-nytt.md).
