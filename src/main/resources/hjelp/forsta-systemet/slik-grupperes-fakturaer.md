---
tittel: Slik grupperes fakturaer
sammendrag: Hver organisasjon med bruk får én faktura, med kundenummer og referanse slik datavarehuset leverer dem fra CRM.
oppdatert: 2026-11-24
relatert: [slik-gjor-du/rett-kundedata, oppslag/kontrollfunn, forsta-systemet/terskelverdi]
nokkelord: [kundenummer, delt referanse, flere fakturaer, fakturamottaker, gruppering]
---
Forsystem lager **én faktura per organisasjon** som har bruk i måneden. Hvert produkt blir én eller flere linjer på fakturaen.

## Kundedata kommer med bruken

Forsystem har ikke noe eget kunderegister. Navn, kundenummer, fakturareferanse («Deres referanse») og bestillingsnummer registreres i CRM og kommer med bruksdataene fra datavarehuset. Fakturaen får verdiene fra importen kjøringen bygger på. Se [Rett kundedata](../slik-gjor-du/rett-kundedata.md).

Kundenummeret er kundens id i Unit4. Det er ikke det samme som organisasjonsnummeret. To organisasjoner kan ha samme kundenummer, og de får likevel hver sin faktura.

## Når kundedata mangler eller ikke stemmer

- Leverer datavarehuset ikke noe kundenummer for organisasjonen, får kjøringen det blokkerende funnet `MANGLER_KUNDENUMMER`, og bruken kommer ikke med.
- Har radene for samme organisasjon ulikt kundenummer, fakturareferanse eller bestillingsnummer, får kjøringen det blokkerende funnet `MOTSTRIDENDE_KUNDEDATA`. Forsystem velger ikke én av verdiene.
- Mangler bare fakturareferansen, får fakturaen advarselen `MANGLER_FAKTURAREFERANSE`.

Kjøringen kan ikke godkjennes med blokkerende funn. Se [Kontrollfunn](../oppslag/kontrollfunn.md).

## Referanse og bestillingsnummer

Alle linjene på fakturaen får samme fakturareferanse og bestillingsnummer. Står bestillingsnummer tomt, brukes fakturareferansen.

Kjøringer fra før kunderegisteret ble fjernet, kan ha fakturaer med ulike referanser per produkt. Slike fakturaer deles i én ordre per referanse i LG04-filen og har advarselen `DELT_REFERANSE`.

## Hvem som mottar fakturaen

Organisasjonen selv mottar fakturaen. Datavarehuset leverer ingen annen fakturamottaker. Om det skal være mulig, er et åpent spørsmål (OQ-7, OQ-28).

## Terskelverdi kommer etter grupperingen

Først grupperes bruken i fakturaer. Deretter sjekkes terskelverdien mot organisasjonens samlede beløp. Se [Terskelverdi](terskelverdi.md).

> [!TIP]
> Reglene her gjelder en vanlig kjøring. En korrigeringskjøring følger andre regler, se [Korrigering og avregning](korrigering-og-avregning.md).
