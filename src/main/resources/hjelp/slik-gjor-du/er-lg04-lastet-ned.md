---
tittel: Er LG04-fila lastet ned?
sammendrag: Slik ser du om, når og av hvem LG04-fila for en kjøring er lastet ned, så den ikke sendes to ganger.
oppdatert: 2026-11-17
rolle: LESER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [slik-gjor-du/hvem-endret-hva, slik-gjor-du/kopier-epost-til-regnskap, manedsrutinen/6-eksporter]
nokkelord: [LG04, nedlastet, lastet ned, sendt, historikk, dobbel sending]
---
Hver gang noen laster ned en eksportfil, logger forsystem det. Slik kan du se om LG04-fila allerede er hentet før du sender den.

## Gjør slik

1. Åpne [Kjøringer](/kjoringer) og trykk [Åpne](knapp:) på den eksporterte kjøringen.
2. Gå til fanen [Historikk](knapp:).
3. Se etter rader med handlingen `LASTET_NED`. Detaljene viser filtypen `LG04` og filnavnet.

Hver rad har tidspunkt og brukernavn. Der ser du hvem som lastet ned, og når. Fant du ingen slik rad med `LG04`, er fila ikke lastet ned fra forsystem.

Du kan også bruke [Hendelseslogg](/hendelseslogg). Velg handlingen `LASTET_NED`, entiteten `FAKTURAKJORING` og eventuelt en dato, og trykk [Filtrer](knapp:). Se [Finn ut hvem som har endret hva](hvem-endret-hva.md).

## Hva fanen Filer viser

På fanen [Filer](knapp:) ligger selve LG04-fila i tabellen **Eksportfiler**, med filnavn og SHA-256. Fanen viser ikke hvem som har lastet den ned. Bruk Historikk til det.

## Hva loggen ikke forteller

> [!IMPORTANT]
> Loggen viser at fila er hentet, ikke at den er sendt til regnskap. Forsystem sender ikke e-posten selv. Avklar med kollegene om fila faktisk er sendt før du sender den på nytt.

Skal du sende den, kan du [kopiere ferdig tekst til e-posten](kopier-epost-til-regnskap.md).

## Hvis noe går galt

- **Ingen eksportfiler på fanen Filer:** kjøringen er ikke eksportert. Se [Eksporter](../manedsrutinen/6-eksporter.md).
- **Flere nedlastinger:** alle logges, også når samme person laster ned igjen. Sjekk tidspunktene.
