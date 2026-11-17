---
tittel: Korrigering og avregning
sammendrag: Forklarer hvordan en måned korrigeres etter eksport, hva som fungerer i dag, og hva som ikke er tilgjengelig i produksjon ennå.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/lag-korrigeringskjoring, nar-noe-gar-galt/eksportert-kan-ikke-forkastes, forsta-systemet/sporbarhet]
nokkelord: [avregning, korrigering, kreditering, kreditnota, negativ, V1, V2, OQ-6]
---
En eksportert kjøring er endelig. Du kan ikke forkaste den eller generere måneden på nytt. Endres bruksdata etter eksport, brukes en korrigeringskjøring.

## Hva som er avklart og bygget

Datavarehuset kan endre en måned som allerede er fakturert. Forsystem lar deg da:

1. Hente måneden på nytt og sammenligne den med det som faktisk er fakturert.
2. Se bare endringene: **Lagt til**, **Endret** eller **Fjernet**, med differanse med fortegn. Fra 100 til 120 er +20, og fra 100 til 80 er -20.
3. Velge hvilke endringer som skal med, og begrunne valget.
4. Lage en korrigeringskjøring (V2, deretter V3 og så videre) som bare inneholder de valgte differansene.

Den opprinnelige kjøringen og alle kildedata blir stående uendret. Endringer du ikke velger, blir ikke borte. De dukker opp igjen ved neste sammenligning. Hver fakturalinje peker tilbake til endringen den korrigerer, og valg, begrunnelse, bruker og tidspunkt står i [Hendelseslogg](../slik-gjor-du/hvem-endret-hva.md).

Slik gjør du det: [Lag en korrigeringskjøring](../slik-gjor-du/lag-korrigeringskjoring.md).

## Hva som ikke er tilgjengelig i produksjon

> [!IMPORTANT]
> En korrigeringskjøring kan ikke eksporteres i produksjon. Eksporter-knappen er erstattet av en melding om at korrigeringen ikke kan eksporteres før avregningsmåten er avklart og testet med regnskap.

Det er åpent hvordan negative beløp og krediteringer skal leveres til Unit4 (OQ-6). Økonomi og VIS arbeider med det. Økonomi har varslet at de vil be om et felt for å oppgi hvilken faktura en kreditering gjelder. Forsystem finner ikke på en løsning. Ingen transaksjonstype, kreditnotakode eller kontering er lagt inn.

Det betyr i praksis:

- Du kan se differansene, velge dem og lage korrigeringsgrunnlaget, og kontrollere det.
- Du kan ikke sende det til regnskap som LG04-fil før OQ-6 er avgjort og testet.
- Er en korrigering nødvendig før det, må Økonomi avgjøre hvordan den håndteres utenfor forsystem.

## Gode å vite

- Bare en komplett eksport fra datavarehuset kan være grunnlag. Forsystems eget CSV-format kan ikke brukes, fordi en rad som mangler, skal tolkes som fjernet.
- Korrigeringen bruker prisene fra den opprinnelige kjøringen.
- Terskelverdien gjelder ikke korrigeringer.
- Det finnes ingen knapp for å avvise en endring uten å fakturere den.
- Før eksport er det enklere: [forkast](../slik-gjor-du/forkast-og-generer-pa-nytt.md) kjøringen og generer på nytt.
- «Testverktøy: nullstill periode» finnes bare i testmiljøet og er ikke en måte å korrigere på i produksjon.
