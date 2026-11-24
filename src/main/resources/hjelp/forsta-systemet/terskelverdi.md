---
tittel: Terskelverdi
sammendrag: Hvorfor kunder med lavt beløp ikke får faktura, hvordan beløpet regnes ut og hva som fortsatt er uavklart.
oppdatert: 2026-11-17
relatert: [slik-gjor-du/endre-pris-eller-terskelverdi, slik-gjor-du/sjekk-ut-advarsler, forsta-systemet/slik-grupperes-fakturaer, oppslag/kontrollfunn, forsta-systemet/prisversjon-og-gyldighet]
nokkelord: [bagatellgrense, minstebeløp, under terskel, faktureres ikke, 40 kr, UNDER_TERSKEL]
---
Terskelverdien er et minstebeløp. En kunde som har brukt for lite i en måned, får ingen faktura den måneden. Det sparer både kunden og regnskap for fakturaer på noen få kroner.

## Slik virker den

- Terskelverdien står på **prisversjonen**. En ny prisversjon starter på 40,00 kr eksl. mva per kunde per måned.
- Forsystem summerer kundens beløp over **alle produkter**, per organisasjonsnummer.
- Er summen **over** terskelverdien, fakturerer forsystem alt kunden har brukt. Er den **lik eller under**, blir det ingen faktura.
- Beløpet regnes eksl. mva.
- Terskelverdien kan endres mens prisversjonen er UTKAST, og låses når den aktiveres.

Terskelverdien sjekkes etter at bruken er gruppert i fakturaer. Se [Slik grupperes fakturaer](slik-grupperes-fakturaer.md).

### Eksempel

Alle tall her er oppdiktede, bare for å vise regelen. Anta en terskelverdi på 40,00 kr.

| Kunde | Melding | Varsling | Samlet | Resultat |
|---|---|---|---|---|
| A | 30,00 | 15,00 | 45,00 | Faktura, for 45 er over 40 |
| B | 25,00 | 15,00 | 40,00 | Ingen faktura, for 40 er ikke over 40 |
| C | 10,00 | | 10,00 | Ingen faktura |

Legg merke til kunde A. Hvert produkt er under terskelverdien, men summen er over, og da faktureres alt.

## Hva du ser i en kjøring

- En advarsel `UNDER_TERSKEL` sier hvor mange kunder som ikke faktureres, og hva de har samlet.
- På fanen **Fakturaer** viser listen **Faktureres ikke (under terskelverdi)** hvem det gjelder, med kundenummer, beløp og kundens samlede sum. Listen kan lastes ned som XLSX eller CSV.
- Bruken deres er ikke borte. I **Avstemming bruksdata** står den som ikke fakturert, med `UNDER_TERSKEL` som årsak.

Advarselen må sjekkes ut før godkjenning. Se [Sjekk ut advarsler](../slik-gjor-du/sjekk-ut-advarsler.md).

Korrigeringskjøringer har ingen terskelverdi.

## Hva som er avklart og hva som ikke er det

Avklart av FEL, etter at Økonomi har valgt det: terskelverdien gjelder under FinMod, den er 40 kr eksl. mva, og den regnes per organisasjonsnummer på tvers av alle Altinn-produktene. Det er den samme regelen som FEL bruker i dag. Den kan justeres, derfor ligger den på prisversjonen.

**Uavklart (OQ-22):** hva som skjer med beløp under terskelverdien. I dag blir de **ikke** overført til neste måned, de faller bort, slik FEL gjør i sitt program. Om Økonomi i stedet vil overføre dem, er ikke bestemt.

> [!IMPORTANT]
> Sjekk med Økonomi før du endrer terskelverdien. Ikke si til en kunde at et beløp under terskelverdien overføres til neste måned. Det gjør det ikke i dag.

Hvordan du endrer verdien, står i [Endre en pris eller terskelverdien](../slik-gjor-du/endre-pris-eller-terskelverdi.md).
