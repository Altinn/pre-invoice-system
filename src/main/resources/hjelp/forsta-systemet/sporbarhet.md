---
tittel: Sporbarhet
sammendrag: Hvordan hver fakturalinje kan følges tilbake til bruksdata, pris og avtale, og hvordan endringer og nedlastinger logges.
oppdatert: 2026-11-17
relatert: [slik-gjor-du/hvem-endret-hva, slik-gjor-du/er-lg04-lastet-ned, slik-gjor-du/finn-en-faktura, forsta-systemet/prisversjon-og-gyldighet, oppslag/filer]
nokkelord: [revisjon, logg, hendelseslogg, historikk, spor, bruksdata-id, pris-id, sha-256, avstemming]
---
En av grunnreglene for systemet er at hver faktura skal kunne spores tilbake til bruksdata, avtaler og priser. Her står det hva det betyr i praksis, og hvor du ser det.

## Fra faktura tilbake til kilden

Hver fakturalinje peker på to ting.

| Peker på | Hvor du ser det |
|---|---|
| **Bruksdata** raden den kom fra (bruksdata-id) | Kolonnen **Bruksdata-id** på fakturaens linjer |
| **Pris** den ble regnet med (pris-id) | Kolonnen **Pris-id** på fakturaens linjer |

Åpne en faktura fra fanen **Fakturaer** på kjøringen, så ser du linjene med begge. En kostnadslinje har ingen pris, så der står det «—». Se [Finn en faktura](../slik-gjor-du/finn-en-faktura.md).

Fakturaen husker også organisasjonsnummeret og navnet på kunden slik de var da kjøringen ble generert. Hver import tar vare på kundedataene datavarehuset leverte, så du kan se hvor kundenummer og referanse kom fra.

### Hvorfor dette hjelper

- Du kan svare på «hvorfor ble det akkurat dette beløpet?» ved å se antall, enhetspris og hvilken prisversjon prisen kom fra.
- Er bruksdata blitt rettet, kan en kjøring genereres på nytt og sammenlignes med den forrige.
- Du kan se hva som **ikke** kom med. **Avstemming bruksdata** på fanen **Kontroller** sammenligner importert bruk med det som ble fakturert, per produkt og type. Hver rad som ikke ble fakturert, står med kontrollfunnet som stoppet den.

Avstemmingen ser bare bruk som kom inn i forsystem. Bruk som datavarehuset filtrerer bort før den når oss, er ikke med. Hvordan bruk kobles til feil tjenesteeier i datavarehuset, kan forsystem ikke oppdage (OQ-12).

## Hendelseslogg: hvem gjorde hva

[Hendelseslogg](/hendelseslogg) viser hver endring i registrene og hver import, generering, godkjenning og eksport. Hver rad har tidspunkt, bruker, handling, hva det gjaldt og detaljer. Du kan filtrere på dato, bruker, entitet og handling, og laste ned loggen som XLSX eller CSV.

Loggen er bare å lese. Den kan ikke redigeres fra appen. Se [Hvem endret hva](../slik-gjor-du/hvem-endret-hva.md).

### Nedlastinger logges også

Å laste ned en eksportfil eller et fakturagrunnlag lager en logglinje, med filtype. Slik kan du se om LG04-filen allerede er hentet, og av hvem. Se [Er LG04 lastet ned?](../slik-gjor-du/er-lg04-lastet-ned.md).

### Historikk per kjøring

Hver kjøring har fanen **Historikk** med alt som er logget for akkurat den kjøringen, nyeste først. Der står også endringer av tekstene på fakturaen med gammel og ny tekst, og hver avkrysning og fjernet avkrysning av advarsler.

## Filene kan kontrolleres

Hver eksportfil arkiveres med en sjekksum (SHA-256). Før noe lagres, leses LG04-filen tilbake og sammenlignes med kjøringen. På en eksportert kjøring kan du trykke **Kontroller arkivert LG04 på nytt**. Da leses den lagrede fila og sjekkes uten at noe endres. Se [Filene forsystem lager](../oppslag/filer.md).

En import fra datavarehuset beholder det som ble hentet, med sjekksum, slik at du kan laste ned nøyaktig det datavarehuset leverte.

## Hva som ikke slettes

Eksporterte kjøringer kan ikke forkastes, og bekreftede importer kan ikke slettes eller endres. Gjør du en ny import for samme periode, blir den forrige merket AVVIST, men den står fortsatt i listen over importer. Hvor lenge filer må oppbevares etter regnskapsreglene, er ikke avklart (OQ-9).

> [!TIP]
> Testverktøyet «nullstill periode» i testmiljøet sletter kjøringer for godt, men registrene og hendelsesloggen beholdes. Selve nullstillingen logges. Verktøyet finnes ikke i produksjon.
