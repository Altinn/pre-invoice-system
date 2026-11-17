---
tittel: Slik grupperes fakturaer
sammendrag: Hvorfor en kunde kan få flere fakturaer, og hvordan kundenummer, produkt og referanse avgjør hvilke linjer som havner sammen.
oppdatert: 2026-11-17
relatert: [slik-gjor-du/legg-til-kundenummerregel, slik-gjor-du/legg-til-fakturareferanse, slik-gjor-du/avvikende-fakturamottaker, oppslag/kontrollfunn, forsta-systemet/terskelverdi]
nokkelord: [kundenummer, servicekode, tilleggstekst, delt referanse, flere fakturaer, avvikende mottaker, kundenummerregel]
---
Under FinMod er det ikke slik at én kunde alltid får én faktura. Forsystem grupperer bruken etter noen få regler. Når du kjenner reglene, forstår du hvorfor antall fakturaer i en kjøring ikke alltid er lik antall kunder.

## Hovedregelen

Bruken slås sammen per **kunde og kundenummer**. Hver slik gruppe blir én faktura, og hvert produkt i gruppen blir én eller flere linjer på den.

Kundenummeret er kundens id i Unit4. Det er ikke det samme som organisasjonsnummeret.

- En kunde med ett kundenummer får vanligvis én faktura.
- En kunde med to kundenumre får to fakturaer.
- To kunder kan dele ett kundenummer, og de får likevel hver sin faktura, for gruppen er kunde pluss kundenummer.

Organisasjonsnummer og kundenummer er altså ikke et til ett. Det er derfor kundenummer er en egen regel i registeret, og ikke et felt på kunden.

## Hvilket kundenummer en linje får

Hver kunde har én eller flere **kundenummerregler**. En regel gjelder enten ett bestemt produkt eller alle produkter, og kan ha en **servicekode** og en **tilleggstekst**.

For hver bruksrad velger forsystem regelen slik:

1. En regel for akkurat dette produktet, hvis den finnes.
2. Ellers en regel som gjelder alle produkter.
3. Finnes ingen av dem, kan ikke raden faktureres.

Eksempel med oppdiktede navn og kundenumre. Kunde A har to regler, 1111 for Melding og 2222 for alle andre produkter. Bruk av Melding havner da på fakturaen for 1111, og bruk av Varsling på fakturaen for 2222. Kunde A får to fakturaer.

Servicekoden lagres på fakturalinjene. Tilleggsteksten brukes når flere virksomheter deler ett kundenummer, slik at de kan skilles fra hverandre.

## Hva som skjer når regelen mangler

Mangler en kunde en passende regel, får kjøringen et blokkerende funn, `MANGLER_KUNDENUMMER`, og bruken for det produktet kommer ikke med. Kjøringen kan ikke godkjennes før det er rettet. Se [Kontrollfunn](../oppslag/kontrollfunn.md). Hvordan du legger inn en regel, står i [Legg til en kundenummerregel](../slik-gjor-du/legg-til-kundenummerregel.md).

Hvis bruken tilhører et organisasjonsnummer uten registrert kunde, gir det `MANGLER_KUNDE` i stedet.

## Referanser og delte fakturaer

Hver linje får fakturareferanse («Deres referanse») og bestillingsnummer fra kunden.

- En referanse uten produkt er standarden for kunden.
- En referanse med produkt gjelder bare det produktet og **erstatter** standarden for det produktet. Står fakturareferanse tomt på en slik rad, får linjen ingen referanse, og det gir en advarsel.
- Står bestillingsnummer tomt, brukes fakturareferansen.

En faktura kan ikke ha mer enn ett par av fakturareferanse og bestillingsnummer i LG04-filen. Har linjene på samme faktura ulike par, deles fakturaen i filen i én ordre per par, og kjøringen får advarselen `DELT_REFERANSE` slik at du ser det før godkjenning. I forsystem er det fortsatt én faktura. Det er først i LG04-filen og i Unit4 at den blir flere. Dette er ikke prøvd mot Unit4 ennå og skal verifiseres i desembers prøvekjøring (OQ-1).

Se [Legg til en fakturareferanse](../slik-gjor-du/legg-til-fakturareferanse.md).

## Hvem som mottar fakturaen

Fakturaen går som regel til kunden selv. Har kunden en **avvikende fakturamottaker** satt, står den virksomhetens organisasjonsnummer som mottaker i stedet. Bruken ligger likevel under kunden som genererte den. Reglene for avvikende mottaker er ikke endelig avklart (OQ-7). Se [Avvikende fakturamottaker](../slik-gjor-du/avvikende-fakturamottaker.md).

## Terskelverdi kommer etter grupperingen

Først grupperes bruken i fakturaer. Deretter sjekkes terskelverdien mot kundens samlede beløp over alle kundenumrene. Se [Terskelverdi](terskelverdi.md).

> [!TIP]
> Reglene her gjelder en vanlig kjøring. En korrigeringskjøring følger andre regler, se [Korrigering og avregning](korrigering-og-avregning.md).
