---
tittel: Korrigering og avregning
sammendrag: Forklarer hvordan en måned korrigeres etter eksport, hva som fungerer i dag, og hva som ikke er tilgjengelig i produksjon ennå.
oppdatert: 2026-11-24
rolle: LESER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/lag-korrigeringskjoring, slik-gjor-du/avregn-et-ar, nar-noe-gar-galt/eksportert-kan-ikke-forkastes, forsta-systemet/sporbarhet]
nokkelord: [avregning, korrigering, kreditering, kreditnota, negativ, V1, V2, OQ-6]
---
En eksportert kjøring er endelig. Du kan ikke forkaste den eller generere måneden på nytt. Endres bruksdata etter eksport, brukes en korrigeringskjøring.

## Avregning av reell transaksjonspris

Som et eget spor kan du avregne et helt år til reell transaksjonspris: du velger måneder og legger inn en prisjustering per produkt, og får et utkast til fakturakjøring. Applikasjonsinfrastruktur avregnes ikke. Se [Avregn et år](../slik-gjor-du/avregn-et-ar.md).

## Hva som er avklart og bygget

Datavarehuset kan endre en måned som allerede er fakturert. Forsystem lar deg da:

1. Hente måneden på nytt og sammenligne den med det som faktisk er fakturert.
2. Se avvikene per kunde: **Lagt til**, **Endret** eller **Fjernet** for bruken, med differanse med fortegn (fra 100 til 120 er +20, og fra 100 til 80 er -20), og **Endret fakturareferanse**, **Endret bestillingsnummer**, **Endret kundenummer** eller **Endret navn** når CRM har andre verdier enn kunden ble fakturert med. En slik endring vises selv om differansen er 0.
3. Velge hvilke kunder som skal med, og begrunne valget. En kunde tas med med alle produktene sine.
4. Lage en korrigeringskjøring (V2, deretter V3 og så videre) som bare inneholder differansene for de valgte kundene.

Den opprinnelige kjøringen og alle kildedata blir stående uendret. Kunder du ikke velger, blir ikke borte. Etter eksport trykker du [Sammenlign på nytt](knapp:), og de står der igjen, uten at du henter data på nytt. En kunde som ikke skal korrigeres, kan avvises med en begrunnelse. Hver fakturalinje peker tilbake til endringen den korrigerer, og valg, begrunnelse, bruker og tidspunkt står i [Hendelseslogg](../slik-gjor-du/hvem-endret-hva.md).

Slik gjør du det: [Lag en korrigeringskjøring](../slik-gjor-du/lag-korrigeringskjoring.md).

## Hva som ikke er tilgjengelig i produksjon

> [!IMPORTANT]
> En korrigeringskjøring kan ikke eksporteres i produksjon. Eksporter-knappen er erstattet av en melding om at korrigeringen ikke kan eksporteres før avregningsmåten er avklart og testet med regnskap.

Det er åpent hvordan negative beløp og krediteringer skal leveres til Unit4 (OQ-6). Økonomi og VIS arbeider med det. Økonomi har varslet at de vil be om et felt for å oppgi hvilken faktura en kreditering gjelder. Forsystem finner ikke på en løsning. Ingen transaksjonstype, kreditnotakode eller kontering er lagt inn.

Det samme gjelder en endret fakturareferanse, bestillingsnummer eller kundenummer. En differanse kan ikke rette dem: en korrigering med differanser ville fått den nye verdien, mens fakturaen den retter, hadde den gamle. Økonomi ønsker helst å kreditere hele den forrige fakturaen og sende en ny med riktige opplysninger. Om det er mulig, og hvordan, avklares med VIS (OQ-6 og OQ-29). Til da kan du se slike endringer, men ikke ta kunden med i en korrigering.

Det betyr i praksis:

- Du kan se differansene, velge dem og lage korrigeringsgrunnlaget, og kontrollere det.
- Du kan ikke sende det til regnskap som LG04-fil før OQ-6 er avgjort og testet.
- Er en korrigering nødvendig før det, må Økonomi avgjøre hvordan den håndteres utenfor forsystem.

## Gode å vite

- Bare en komplett eksport fra datavarehuset kan være grunnlag. Forsystems eget CSV-format kan ikke brukes, fordi en rad som mangler, skal tolkes som fjernet.
- Korrigeringen bruker prisene fra den opprinnelige kjøringen.
- Kundenummer, fakturareferanse og bestillingsnummer hentes fra den nye eksporten, slik CRM har dem nå. Er de endret siden kunden ble fakturert, kan kunden ikke tas med i en korrigering ennå (se over). Er en kunde helt borte fra den nye eksporten, krediteres den med verdiene den sist ble fakturert med.
- Terskelverdien gjelder ikke korrigeringer.
- En avvist kunde faktureres ikke på nytt. Avvisningen kan angres så lenge sammenligningen er åpen.
- Før eksport er det enklere: [forkast](../slik-gjor-du/forkast-og-generer-pa-nytt.md) kjøringen og generer på nytt.
- «Testverktøy: nullstill periode» finnes bare i testmiljøet og er ikke en måte å korrigere på i produksjon.
