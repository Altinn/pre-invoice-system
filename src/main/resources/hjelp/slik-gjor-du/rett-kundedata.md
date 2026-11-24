---
tittel: Rett kundedata
sammendrag: Navn, kundenummer, fakturareferanse og bestillingsnummer kommer fra CRM via datavarehuset. Slik får du rettet en feil eller noe som mangler.
oppdatert: 2026-11-24
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/importer-periode-pa-nytt, slik-gjor-du/forkast-og-generer-pa-nytt, oppslag/kontrollfunn, forsta-systemet/slik-grupperes-fakturaer]
nokkelord: [kunde, kunderegister, CRM, kundenummer, fakturareferanse, Deres referanse, bestillingsnummer, 25 tegn, MANGLER_KUNDENUMMER, MOTSTRIDENDE_KUNDEDATA, FOR_LANG_REFERANSE, MANGLER_FAKTURAREFERANSE]
---
Forsystem har ikke et eget kunderegister. Kundedata registreres i CRM og kommer til forsystem sammen med bruksdataene fra datavarehuset. Hver import tar med seg, per organisasjon:

- navnet
- kundenummeret i Unit4
- fakturareferansen (på fakturaen kalt «Deres referanse»)
- bestillingsnummeret

Når du genererer en kjøring, får fakturaen verdiene fra importen kjøringen bygger på. Du kan ikke endre dem i forsystem.

> [!IMPORTANT]
> Er en verdi feil eller mangler, rettes den i CRM. Først når datavarehuset har fått rettelsen, kan du importere måneden på nytt og generere kjøringen på nytt.

## Slik ser du hva som mangler

Forhåndsvisningen av en import har avsnittet **Kundedata** når noe mangler eller ikke stemmer:

- **mangler kundenummer**: organisasjonen får det blokkerende funnet `MANGLER_KUNDENUMMER` når kjøringen genereres
- **motstridende kundedata i fila**: radene for samme organisasjon har ulikt kundenummer, fakturareferanse eller bestillingsnummer. Det gir det blokkerende funnet `MOTSTRIDENDE_KUNDEDATA`. Forsystem velger aldri én av verdiene.
- **fakturareferanse eller bestillingsnummer over 25 tegn**: det er plassen i LG04-filen. Det gir det blokkerende funnet `FOR_LANG_REFERANSE`. Forsystem korter ikke verdien.

Ingen av dem hindrer importen. Mangler bare fakturareferansen, får fakturaen advarselen `MANGLER_FAKTURAREFERANSE`.

## Gjør slik

1. Finn organisasjonsnummeret og hva som er feil, i forhåndsvisningen eller på fanen **Kontroller** i kjøringen.
2. Be den som forvalter kundedata i CRM om å rette det.
3. Når rettelsen er med i datavarehuset, importer måneden på nytt, se [Importer en periode på nytt](importer-periode-pa-nytt.md). Finnes det en kjøring for måneden, forkaster du den først, se [Forkast og generer på nytt](forkast-og-generer-pa-nytt.md).
4. Generer kjøringen på nytt.

## Står bestillingsnummer tomt?

Da får fakturaen fakturareferansen som bestillingsnummer. Det skjer når kjøringen genereres. Importen beholder det datavarehuset leverte.

## Det som ikke finnes lenger

Tidligere kunne forsystem ha fakturareferanse per produkt, flere kundenummer for én organisasjon (med servicekode og tilleggstekst) og en annen fakturamottaker enn organisasjonen selv. Datavarehuset leverer ikke disse feltene, så de er fjernet. Hver organisasjon får nå én faktura, og organisasjonen selv mottar den. Om noe av dette trengs, må det komme fra CRM (OQ-28).

## Slik ser du at det gikk bra

Avsnittet **Kundedata** er borte fra forhåndsvisningen, og kjøringen har ingen `MANGLER_KUNDENUMMER` eller `MOTSTRIDENDE_KUNDEDATA`. Åpne fakturaen under **Fakturaer** for å se navn, kundenummer og referanse.
