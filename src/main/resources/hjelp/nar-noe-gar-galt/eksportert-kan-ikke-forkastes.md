---
tittel: Eksportert kan ikke forkastes
sammendrag: Hvorfor en eksportert kjøring ikke kan forkastes, og hva du gjør hvis tallene må rettes etterpå.
oppdatert: 2026-11-17
relatert: [slik-gjor-du/lag-korrigeringskjoring, forsta-systemet/korrigering-og-avregning, kom-i-gang/prov-selv, oppslag/statuser]
nokkelord: [forkast, eksportert, endelig, korrigering, avregning, nullstill]
---
## Hva du ser

Du finner ingen [Forkast](knapp:) på en eksportert kjøring. Prøver du å forkaste den likevel, for eksempel fra en gammel side, får du meldingen «En eksportert kjøring kan ikke forkastes».

## Hvorfor

Etter eksport er LG04-fila laget og arkivert, og kan allerede være sendt videre. En eksportert kjøring er derfor endelig, og historikken skal bevares. Hvilken mekanisme som skal brukes til å rette opp tall som allerede er fakturert, er ikke avklart (OQ-6).

## Hva du gjør

- **I produksjon:** Kjøringen står. Trenger tallene å rettes, kan du lage en korrigeringskjøring fra en sammenligning av bruksdata. Se [Lag en korrigeringskjøring](../slik-gjor-du/lag-korrigeringskjoring.md). Hvordan en slik korrigering skal gå til regnskap, er foreløpig ikke avklart. Les [Korrigering og avregning](../forsta-systemet/korrigering-og-avregning.md) og ta kontakt med forvaltningen.
- **I tt02 eller lokalt:** Du kan bruke Nullstill periode for å slette måneden og kjøre den på nytt. Se [Prøv selv](../kom-i-gang/prov-selv.md). Den finnes ikke i produksjon.

> [!IMPORTANT]
> Nullstill periode sletter alt for måneden for godt. Bruk den aldri for å «rydde opp» i en ekte måned.
