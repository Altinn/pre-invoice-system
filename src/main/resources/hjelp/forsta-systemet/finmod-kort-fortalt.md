---
tittel: FinMod kort fortalt
sammendrag: Bakgrunnen for forsystem, hvem som eier hvilke data, og hvilke spørsmål som fortsatt er åpne.
oppdatert: 2026-11-17
relatert: [kom-i-gang/hva-er-forsystem, forsta-systemet/slik-grupperes-fakturaer, forsta-systemet/terskelverdi, forsta-systemet/korrigering-og-avregning, oppslag/ordliste]
nokkelord: [finmod, prismodell, altinn, datavarehus, unit4, crm, tjenesteeier, avregning, åpne spørsmål]
---
Denne artikkelen gir bakgrunnen for hvorfor forsystem finnes og hvordan det passer inn. Den oppgir ingen priser, datoer eller regler utover det som er bekreftet. Det som ikke er avklart, står som åpne spørsmål (OQ) og kan ha endret seg.

## Hva som faktureres

Fra **1. januar 2027** gjelder den nye prismodellen **FinMod** for Altinn-produktene: melding, formidling, varsling, autorisasjon og Studio (inkludert applikasjonsinfrastruktur). Tjenesteeierne, altså offentlige virksomheter og kommuner, faktureres **månedlig** for faktisk bruk måneden før. Fakturaen er bruk multiplisert med enhetspris, pluss videreførte kostnader (Azure og SMS).

Første fakturering er tidlig i februar 2027, for januar. Priser fastsettes en gang i året. Prislisten for 2027 er publisert og kan legges inn i forsystem.

De eldre tjenestene (digital postkasse, e-signering, Altinns nåværende månedsfakturering og BOD) hører ikke til her. De fortsetter på det gamle systemet, som FEL driver.

## Hvor forsystem står

```text
Kildedata → Datavarehus → forsystem → Unit4 → faktura til tjenesteeier
```

1. **Kildedata**, målepunkter og kostnader i Altinn-produktene.
2. **Datavarehuset** samler og regner ut, sjekker mot aktive avtaler, filtrerer bort bruk som ikke skal faktureres, og gir ut det fakturerbare grunnlaget.
3. **forsystem** henter kundeinformasjon, setter på priser, kjører kontroller og lager fakturagrunnlag.
4. **Unit4** lager selve fakturaen, bokfører og følger opp betaling.
5. Tjenesteeieren får fakturaen og en detaljvisning av grunnlaget.

Prinsippet er at datavarehuset avgjør *hva* som kan faktureres, og forsystem avgjør *hvordan* det faktureres. Hver faktura skal kunne spores tilbake til bruksdata, avtaler og priser. Se [Sporbarhet](sporbarhet.md).

## Hvem eier hvilke data

| Data | Hvor den hører hjemme |
|---|---|
| Kunde- og avtaleinformasjon | CRM (Dynamics 365). Kundedataene kommer til forsystem sammen med bruken fra datavarehuset. Forsystem har ikke noe eget kunderegister. Se [Rett kundedata](../slik-gjor-du/rett-kundedata.md). |
| Bruk og kostnader | Datavarehuset. Det fullstendige grunnlaget er klart den 6. i hver måned, fordi Azure-kostnader først er endelige fem dager ut i måneden. Til datavarehuset er klart til å brukes, brukes CSV-opplasting. |
| SMS-kostnader | Ikke avklart helt (OQ-3). |
| Priser, prisversjoner og produkter | **forsystem.** Det finnes ikke noe annet produktregister, så det vedlikeholdes her. |
| Kundenummer, artikkel-id, fakturanummer og status | Unit4. Artikkel-id er en kopi i forsystem. |

Derfor kan en kjøring tidligst gjøres den 6. i måneden etter.

## Regler som preger systemet

1. **Kundedata kommer fra CRM.** Navn, kundenummer, fakturareferanse og bestillingsnummer følger med bruken fra datavarehuset. Hver organisasjon får én faktura.
2. **Kundenummer er ikke det samme som organisasjonsnummer.** Flere virksomheter kan dele ett kundenummer og får likevel hver sin faktura.
3. **Datavarehuset avgjør hva som kan faktureres.** Det tar bare med bruk under aktiv avtale i CRM. Forsystem kontrollerer at kundenummer finnes og at kundedataene ikke er motstridende.
4. **Det som ikke er med.** Avvikende fakturamottaker, referanser per produkt og flere kundenumre per organisasjon leveres ikke av datavarehuset (OQ-7, OQ-28).

Se [Slik grupperes fakturaer](slik-grupperes-fakturaer.md).

## Hvordan filene går videre

Forsystem lager LG04-filen for Unit4 og detaljvisningene som PDF. Disse lastes ned fra appen og sendes videre av en person. LG04-filen sendes med e-post til regnskap, som leser den inn i Unit4. Unit4 lager en rapport, og er den riktig, går fakturaene ut som EHF. Se [Filene forsystem lager](../oppslag/filer.md).

## Det som fortsatt er åpent

Dette er en kortliste. Den fulle, oppdaterte listen ligger i prosjektets dokumentasjon, og spørsmålene kan endre seg.

| Tema | Spørsmål |
|---|---|
| Kontering | Konto og dimensjoner per produkt avgjøres av Økonomi (OQ-2). De legges inn på produktene i appen. |
| Avregning | Hvordan korreksjon, kreditering og tilbakevirkende priser skal fungere (OQ-6). Se [Korrigering og avregning](korrigering-og-avregning.md). |
| Terskelverdi | Hva som skjer med beløp under terskelen (OQ-22). Se [Terskelverdi](terskelverdi.md). |
| Avvikende mottaker | Reglene for å fakturere en annen virksomhet (OQ-7). |
| Kunder fra CRM | Hvordan og når kundedata kan hentes derfra (OQ-5). |
| Kobling bruk til tjenesteeier | Hvordan bruk som er koblet til feil virksomhet, oppdages og rettes (OQ-12). Forsystem kan ikke se det selv. |
| Tekster og lengder på fakturaen | Hvilken lengdegrense som gjelder på den trykte fakturaen (OQ-23). |

> [!IMPORTANT]
> Bruk ikke denne artikkelen som kilde til priser eller kontering. De hentes fra prisversjonen og produktregisteret i appen, og eies av Økonomi.
