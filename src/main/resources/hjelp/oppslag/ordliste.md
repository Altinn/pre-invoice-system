---
tittel: Ordliste
sammendrag: Forklaring av fagordene du møter i forsystem, i alfabetisk rekkefølge.
oppdatert: 2026-11-17
relatert: [forsta-systemet/finmod-kort-fortalt, kom-i-gang/hva-er-forsystem, oppslag/statuser, oppslag/kontrollfunn]
nokkelord: [begrep, forkortelse, ordforklaring, hva betyr, tjenesteeier, kontering, artikkel-id]
---
Ordene her er brukt i appen og i læringssenteret. Forklaringene er korte. Der det finnes en artikkel som utdyper, står en lenke.

| Ord | Betyr |
|---|---|
| **Artikkel-id** | Unit4s artikkelnummer, som styrer riktig kontering. Det opprettes i Unit4 og registreres på produktet i forsystem som en kopi. Flere produkter kan dele samme nummer. |
| **Avregning** | Mekanisme for korrigering og kreditering etter at en faktura er eksportert. Hvordan den skal fungere, er ikke avklart (OQ-6). Se [Korrigering og avregning](../forsta-systemet/korrigering-og-avregning.md). |
| **Avtalestatus** | Om kunden har en aktiv avtale i CRM. Datavarehuset tar bare med bruk under aktiv avtale. Forsystem ser ikke statusen selv. |
| **Bestillingsnummer** | Kundens eget bestillings- eller ordrenummer, som skrives på fakturaen. Er det tomt, brukes fakturareferansen. Maks 25 tegn. |
| **Bruksdata** | Bruken som skal faktureres, per organisasjonsnummer, produkt og periode. Enten et antall (volum) eller en kostnad. |
| **DFØ** | Direktoratet for økonomistyring. Driver Unit4 for Digdir. |
| **Eksportfil** | En fil forsystem lager ved eksport og arkiverer, for eksempel LG04. Se [Filene forsystem lager](filer.md). |
| **Fakturagrunnlag** | De beregnede linjene bak en faktura. Det er dette forsystem lager. |
| **Fakturakjøring (kjøring)** | Én faktureringsrunde for én periode. Se [Statuser](statuser.md). |
| **Fakturamottaker** | Den som mottar fakturaen. Det er organisasjonen selv. Om en annen virksomhet skal kunne motta den, er ikke avklart (OQ-7). |
| **Fakturareferanse** | Kundens referanse på fakturaen («Deres referanse»). Kommer fra CRM via datavarehuset. Se [Rett kundedata](../slik-gjor-du/rett-kundedata.md). |
| **FEL** | Enheten i Digdir som driver dagens fakturering på det gamle systemet. |
| **FinMod** | Programmet for ny prismodell som dette arbeidet hører til. Se [FinMod kort fortalt](../forsta-systemet/finmod-kort-fortalt.md). |
| **Kontering** | Regnskapskodene på et produkt (konto, dimensjoner og artikkel-id). Uten kontering kan ikke en kjøring godkjennes. Hvilke koder som skal brukes, avgjøres av Økonomi (OQ-2). |
| **Kontrollfunn** | Resultatet av en automatisk kontroll, enten blokkerende eller en advarsel. Se [Kontrollfunn](kontrollfunn.md). |
| **Kundenummer** | Kundens id i Unit4 (oppgitt til 4 tegn, formatet kontrolleres ikke i forsystem ennå, OQ-10). Kommer fra CRM via datavarehuset. Hver organisasjon får én faktura. Se [Slik grupperes fakturaer](../forsta-systemet/slik-grupperes-fakturaer.md). |
| **LG04** | Importformat med fast bredde som Unit4 leser inn, via regnskap. Se [Filene forsystem lager](filer.md). |
| **Orgnr** | Organisasjonsnummer, ni siffer. Identifiserer den juridiske enheten. |
| **Periode** | Faktureringsmåneden. Alltid første dag i måneden, og alltid valgt av deg, aldri hentet fra dagens dato. |
| **Prisversjon** | Prisliste med gyldighetsperiode og terskelverdi. Livssyklus er utkast, aktiv, arkivert. Se [Prisversjon og gyldighet](../forsta-systemet/prisversjon-og-gyldighet.md). |
| **Terskelverdi** | Beløpet en kunde må ha over i en måned for å få faktura. Står på prisversjonen. Se [Terskelverdi](../forsta-systemet/terskelverdi.md). |
| **Tjenesteeier** | Virksomheten som faktureres. Kalles også kunde i appen. |
| **Unit4** | Økonomisystemet som lager selve fakturaene og står for bokføring. Tidligere kalt Agresso. |
| **Økonomi** | Fagmiljøet som eier priser, kontering og beslutninger om fakturamottaker og fakturareferanse. |

> [!TIP]
> Mangler du et ord? Søk i læringssenteret, eller se [Forstå systemet](../forsta-systemet/slik-grupperes-fakturaer.md) for forklaringer på hvorfor systemet virker som det gjør.
