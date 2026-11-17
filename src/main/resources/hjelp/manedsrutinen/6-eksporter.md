---
tittel: Eksporter og send videre
sammendrag: Du eksporterer kjøringen, kontrollerer at LG04-fila stemmer, laster ned filene og sender LG04 til regnskap sammen med tallene fra forsystemet.
oppdatert: 2026-11-17
rolle: GODKJENNER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/5-godkjenn, slik-gjor-du/kopier-epost-til-regnskap, slik-gjor-du/er-lg04-lastet-ned, nar-noe-gar-galt/eksport-stoppet, oppslag/filer, forsta-systemet/finmod-kort-fortalt]
nokkelord: [eksport, lg04, pdf, xlsx, csv, avstemming, dfø, unit4, regnskap, kopier, e-post]
---
Eksporten lager filene som sendes videre til regnskap, som leser dem inn i Unit4. Forsystemet lager selve fakturagrunnlaget. Fakturaen lages i Unit4.

## Hvem

Du trenger rollen GODKJENNER for å eksportere. Alle roller kan laste ned filene etterpå.

## Før du starter

Kjøringen er [godkjent](5-godkjenn.md) og har status «Klar til eksport».

## Gjør slik

1. Åpne kjøringen under [Kjøringer](/kjoringer) og trykk [Eksporter](knapp:). Bekreft med [Ja, eksporter](knapp:).
2. Før noe lagres, leser forsystemet LG04-fila tilbake og sammenligner den med kjøringen. Stemmer den, lages og arkiveres filene.
3. Kontroller at siden sier «LG04 stemmer med fakturagrunnlaget». Tallene under **LG04-avstemming** på fanen Kontroller viser antall ordrer og beløp i fila og i kjøringen. Vil du sjekke at den arkiverte fila er uendret, trykk [Kontroller arkivert LG04 på nytt](knapp:).
4. Åpne fanen [Filer](knapp:) og trykk [Last ned](knapp:) ved hver fil. Eksporten lager fire filer, hver med SHA-256:
   - **LG04**, tekstfila som regnskap leser inn i Unit4. Den er allerede riktig kodet, så du skal ikke konvertere den.
   - **PDF**, en zip-fil med én detaljvisning per faktura.
   - **CSV** og **XLSX**, med alle linjene, til kontroll.
5. Under «Til e-post til regnskap» på Oversikt står område, buntnummer, periode, antall fakturaer og samlet beløp eksl. og inkl. mva. Trykk [Kopier e-post](knapp:) og lim teksten inn i en e-post. Forsystemet sender ikke e-posten selv. Se [Kopier e-post til regnskap](../slik-gjor-du/kopier-epost-til-regnskap.md).
6. Legg ved LG04-fila og send e-posten til regnskap. Slik gjøres det i dag, og fila går til regnskap i VIS, ikke direkte til DFØ (OQ-8). Regnskap leser fila inn i Unit4, og fakturaene sendes derfra.
7. PDF-ene kan du dele med tjenesteeierne etter avtale.

> [!WARNING]
> Eksporterte kjøringer kan ikke forkastes. Hvordan feil i en eksportert måned rettes, er ikke avklart (OQ-6).

## Slik ser du at det gikk bra

Kjøringen har status «Eksportert». Filer-fanen viser fire filer med SHA-256, og avstemmingen sier at LG04 stemmer. Du kan alltid se om LG04 er lastet ned i [Hendelseslogg](/hendelseslogg). Se [Er LG04 lastet ned?](../slik-gjor-du/er-lg04-lastet-ned.md).

## Hvis noe går galt

- **Eksporten stopper med en rød melding.** Ingen filer lages, og kjøringen er fortsatt «Klar til eksport». Dette er en feil i forsystemet, ikke i dataene dine. Kontakt forsystem-teamet, og trykk Eksporter på nytt når feilen er rettet. Se [Eksport stoppet](../nar-noe-gar-galt/eksport-stoppet.md).
- **Regnskap sender en feilmelding fra Unit4.** Vanlige årsaker er et ukjent kundenummer eller feil kontering. Hvordan en eksportert måned rettes, er ikke avklart (OQ-6), så ta kontakt med forsystem-teamet før du gjør noe mer.
- **Du sendte feil fil.** Last ned fila på nytt fra Filer-fanen. Den arkiverte fila er den som ble avstemt.
