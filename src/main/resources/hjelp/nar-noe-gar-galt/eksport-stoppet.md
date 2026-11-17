---
tittel: Eksport stoppet
sammendrag: Hva det betyr når eksporten stopper fordi LG04-fila ikke stemmer med kjøringen, og hvem som må gjøre noe.
oppdatert: 2026-11-17
rolle: GODKJENNER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/6-eksporter, slik-gjor-du/er-lg04-lastet-ned, oppslag/filer]
nokkelord: [eksport, lg04, avstemming, stoppet, feil i forsystem]
---
## Hva du ser

Etter [Eksporter](knapp:) står det en rød melding øverst på kjøringen. Den begynner med «Eksport stoppet: LG04-filen har … ordrer / … kr, fakturagrunnlaget har … ordrer / … kr.» Kanskje følger noen konkrete feil i filen. Til slutt står det at ingen filer er laget, og at kjøringen fortsatt er godkjent.

## Hvorfor

Før noe lagres, leser forsystem LG04-fila tilbake og sammenligner antall ordrer og beløp med kjøringen. Stemmer de ikke, stoppes eksporten. Fila bygges fra kjøringens egne linjer, så dette er alltid en **feil i forsystem**. Det er ikke noe galt med dataene dine.

## Hva du gjør

1. **Ingenting må gjøres om.** Ingen filer er laget, og kjøringen er fortsatt «Klar til eksport». Du trenger ikke å forkaste eller generere på nytt.
2. Ta kontakt med forvaltningen, og oppgi perioden og meldingen slik den står, med tallene.
3. Når feilen er rettet, åpner du den samme kjøringen og trykker [Eksporter](knapp:) på nytt.

Hendelsen blir logget i [Hendelseslogg](/hendelseslogg), med tallene fra begge sider.

> [!WARNING]
> Ikke bruk en LG04-fil fra en tidligere eksport av samme måned i stedet. Send bare filer fra en vellykket eksport.

## Slik ser du at det gikk bra

Etter en vellykket eksport står det «LG04 stemmer med fakturagrunnlaget» på kjøringen. Se [Eksporter](../manedsrutinen/6-eksporter.md).
