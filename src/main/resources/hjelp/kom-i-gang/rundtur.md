---
tittel: Rundtur i menyen
sammendrag: Et raskt overblikk over hver del av toppmenyen, i den rekkefølgen du bruker dem gjennom en måned.
oppdatert: 2026-11-17
apne-i-appen: /
apne-i-appen-tekst: Gå til forsiden
relatert: [kom-i-gang/hva-er-forsystem, manedsrutinen/1-sjekk-registrene, oppslag/statuser]
nokkelord: [meny, navigasjon, toppmeny, oversikt]
---
Toppmenyen er lik på alle sider. Rekkefølgen følger arbeidsflyten: først registrene, så bruken, så kjøringene.

## Forsiden

[Forsiden](/) viser hvor faktureringen står for **én periode** og hva som er neste steg. Perioden velges alltid ut fra dataene, aldri ut fra dagens dato: den eldste måneden med en kjøring som ikke er ferdig (generert eller godkjent), ellers den nyeste måneden det finnes bruksdata eller en kjøring for. Korrigeringskjøringer bytter ikke periode.

| Del | Hva den viser |
|---|---|
| Velkomst | En setning om hvor perioden står, og én knapp til neste steg. Knappen tilpasses rollen din. |
| Månedsløpet | Bruksdata hentet, generert, kontrollert og godkjent, eksportert. |
| Nøkkeltall | Fakturagrunnlag med endring mot forrige eksporterte periode, antall fakturaer og hvor mange som er under terskel, transaksjoner, og når bruksdata sist ble hentet fra datavarehuset. |
| Per produkt | Fakturagrunnlaget fordelt på produkt, eller bruksvolumet før kjøringen er generert. |
| Trenger oppmerksomhet | Blokkerende funn, advarsler som ikke er vurdert, uferdige korrigeringer, ny bruksdata som avviker fra det som er fakturert, nyere bruksdata, produkter uten konto eller artikkel-id, og manglende aktiv prisversjon. Tom liste betyr «Alt i rute». |
| Siste aktivitet | De seks nyeste hendelsene fra hendelsesloggen. Nedlastinger er utelatt. |

Kortet **Ny i forsystem?** leder hit, til Læringssenteret.

## Produkter

[Produkter](/produkter) viser de fem Altinn-produktene som faktureres, med kontering (konto, dimensjoner og artikkel-id). Manglende kontering blokkerer fakturering. Under **Kildenavn** kobler du produktnavnene fra datavarehuset til produktene i forsystem, slik at en import fra datavarehuset ikke avvises.

## Prisversjoner

[Prisversjoner](/prisversjoner) er de årlige prislistene. En prisversjon starter som utkast, blir aktiv og senere arkivert, og den har en egen terskelverdi for hvem som får faktura. En kjøring bruker den aktive versjonen som dekker månedens første dag.

## Bruksdata

[Bruksdata](/bruksdata) er der du henter bruken for en måned fra datavarehuset eller laster opp en CSV-fil. Du får en forhåndsvisning med summer og avvik før noe lagres, og du må bekrefte importen.

## Kjøringer

[Kjøringer](/kjoringer) er der selve faktureringen skjer. Du genererer en kjøring for en måned, går gjennom kontrollene, godkjenner og eksporterer. Hver kjøring har fem faner:

- [Oversikt](knapp:) viser fremdriften, neste steg og sammendraget, og her endrer du tekstene på fakturaen.
- [Kontroller](knapp:) viser kontrollfunn, avstemming av bruksdata og LG04-avstemming.
- [Fakturaer](knapp:) lister fakturaene, og kundene som ikke får faktura fordi de er under terskelverdien.
- [Filer](knapp:) har fakturagrunnlaget som regneark og eksportfilene.
- [Historikk](knapp:) viser hva som har skjedd med kjøringen.

## Hendelseslogg

[Hendelseslogg](/hendelseslogg) er en skrivebeskyttet logg over alle endringer, import, generering, godkjenning, eksport og nedlasting. Du kan filtrere den og laste den ned som regneark.

## Hjelp

**Hjelp** øverst til høyre åpner Læringssenteret, som du leser nå. Her finner du månedsrutinen steg for steg, veiledninger for enkeltoppgaver og oppslag om kontrollfunn, statuser og filformat.

> [!TIP]
> Det finnes ingen kundeliste i menyen. Kundeopplysninger vedlikeholdes i CRM (OQ-5).
