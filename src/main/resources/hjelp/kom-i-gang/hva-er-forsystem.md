---
tittel: Hva er forsystem?
sammendrag: En kort innføring i hva forsystem gjør, hvordan en måned går fra bruksdata til LG04-fil, og hva systemet ikke gjør.
oppdatert: 2026-11-17
apne-i-appen: /
apne-i-appen-tekst: Gå til forsiden
relatert: [kom-i-gang/rundtur, manedsrutinen/1-sjekk-registrene, oppslag/ordliste]
nokkelord: [fakturagrunnlag, forsystem, introduksjon, finmod, lg04]
---
forsystem er forsystemet som bygger **fakturagrunnlaget** for Altinn-produktene under FinMod-prismodellen. Du vedlikeholder registre, henter inn bruk, genererer grunnlaget, går gjennom kontrollene, godkjenner og eksporterer filene som skal videre til Unit4 via DFØ.

Fakturering skjer én gang i måneden, basert på bruken i **forrige** måned. Du velger alltid selv hvilken måned du jobber med. Systemet gjetter den aldri ut fra dagens dato.

## Slik går en måned

1. **Sjekk registrene.** Produktene har kontering, og en prisversjon som dekker måneden er aktiv. Se [Sjekk registrene](../manedsrutinen/1-sjekk-registrene.md).
2. **Hent bruksdata.** Hent bruken fra datavarehuset, eller last opp en CSV-fil, og bekreft importen. Se [Hent bruksdata](../manedsrutinen/2-hent-bruksdata.md).
3. **Generer kjøringen.** Forsystemet bygger fakturaer fra bruken og den aktive prisversjonen. Se [Generer kjøringen](../manedsrutinen/3-generer.md).
4. **Gå gjennom kontrollfunn.** Blokkerende funn må løses. Advarsler må sjekkes ut. Se [Kontrollfunn](../manedsrutinen/4-kontrollfunn.md).
5. **Godkjenn.** Når kontrollene er ryddet, godkjenner en person med rollen GODKJENNER. Se [Godkjenn](../manedsrutinen/5-godkjenn.md).
6. **Eksporter og send videre.** Du får LG04-filen, PDF-ene og regnearkene. En person sender LG04-filen videre. Se [Eksporter](../manedsrutinen/6-eksporter.md).

> [!TIP]
> Alle beløp i forsystem er eksklusive mva.

## Hva forsystem ikke gjør

- **Sender ingen fakturaer.** Forsystem lager grunnlaget. Selve fakturaen lages i Unit4. Du laster ned LG04-filen og sender den videre selv, med en e-post til regnskap. Du kan kopiere e-posttekstene fra kjøringen, men forsystem sender ikke e-posten.
- **Er ikke kunderegisteret.** Kundeopplysninger som navn, kundenummer og referanse hører hjemme i CRM og kommer til forsystem sammen med bruken fra datavarehuset. De kan ikke endres i forsystem, se [Rett kundedata](../slik-gjor-du/rett-kundedata.md).
- **Bestemmer ikke kontering eller priser.** Det gjør Økonomi. Så lenge kontering ikke er bekreftet (OQ-2), stopper kontrollene deg heller enn å gjette.

Vil du øve først? Se [Prøv selv](prov-selv.md). Vil du se hva som finnes i menyen? Se [Rundtur](rundtur.md).
