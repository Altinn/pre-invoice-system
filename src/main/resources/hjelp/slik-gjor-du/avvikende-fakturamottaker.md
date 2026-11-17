---
tittel: Send fakturaen til en annen enn kunden
sammendrag: Slik registrerer du at en annen virksomhet enn bruksenheten skal motta fakturaen.
oppdatert: 2026-11-17
rolle: FORVALTER
relatert: [slik-gjor-du/legg-til-kundenummerregel, forsta-systemet/slik-grupperes-fakturaer, oppslag/kontrollfunn]
nokkelord: [fakturamottaker, avvikende mottaker, orgnr, kunde]
---
Vanligvis faktureres den virksomheten som har bruken. Noen ganger skal en annen juridisk enhet motta fakturaen, for eksempel når en kommune betaler for et interkommunalt samarbeid. Da registrerer du en avvikende fakturamottaker på kunden. Du trenger rollen FORVALTER.

> [!IMPORTANT]
> Kundesiden har ingen lenke i menyene i dag. Forvaltningen åpner den med en direkte lenke de har fått. Mangler du lenken, spør forvaltningen.

## Gjør slik

1. Åpne kundesiden. Under **Grunndata** finner du feltene.
2. Skriv organisasjonsnummeret til mottakeren i **Fakturamottaker orgnr**. Det må være 9 siffer, og feltet er valgfritt.
3. Trykk [Lagre](knapp:).

Vil du fjerne en avvikende mottaker, tømmer du feltet og trykker [Lagre](knapp:). Fakturaen går da til kunden selv.

## Hva skjer ved generering

Bruken er fortsatt kundens, og kunden står som kunde på fakturagrunnlaget. Mottakerens organisasjonsnummer settes inn som fakturamottaker. Mottakeren lagres på fakturaen når kjøringen genereres. Retter du feltet senere, må du generere på nytt for å få det med.

Forsystem sjekker ikke om mottakerens organisasjonsnummer finnes eller er riktig. Kontroller det mot Enhetsregisteret før du lagrer.

## Andre felt under Grunndata

- **Virksomhetsnavn**: er navnet ulikt det offisielle i Enhetsregisteret (BRREG), får du en advarsel etter lagring. Den hindrer ikke lagringen.
- **Avtalestatus**: AKTIV, INAKTIV eller UNDER_AVKLARING. Har kunden bruk i perioden og statusen ikke er aktiv, stopper `INAKTIV_AVTALE` godkjenningen.

## Slik ser du at det gikk bra

Siden laster på nytt med orgnr i feltet. I en ny kjøring fra [Kjøringer](/kjoringer) står mottakerens organisasjonsnummer i kolonnen «Mottaker (orgnr)» under fanen [Fakturaer](knapp:). Se [Kontrollfunn](../oppslag/kontrollfunn.md) om noe stopper.
