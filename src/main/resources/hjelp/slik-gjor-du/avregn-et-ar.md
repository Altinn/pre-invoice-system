---
tittel: Avregn et år
sammendrag: Slik avregner du reell transaksjonspris for valgte måneder og lager et utkast til fakturakjøring.
oppdatert: 2026-11-25
rolle: FORVALTER
apne-i-appen: /avregning
apne-i-appen-tekst: Åpne Avregning
relatert: [forsta-systemet/korrigering-og-avregning, manedsrutinen/4-kontrollfunn, slik-gjor-du/endre-fakturatekster]
nokkelord: [avregning, prisjustering, reell transaksjonspris, måneder, applikasjonsinfrastruktur, Studio, utkast]
---
Avregning fakturerer reell transaksjonspris, som bygger på faktiske kostnader og volum. Du velger hvilke måneder som skal avregnes og legger inn en prisjustering per produkt. Forsystem lager et utkast til fakturakjøring som du kontrollerer, godkjenner og eksporterer som vanlig.

## Slik regnes beløpet

For hver kunde og hvert produkt er beløpet **bruken i de valgte månedene ganger prisjusteringen**. Prisjusteringen oppgis i kroner per enhet, eksklusive mva. Et negativt tall gir kreditt. Produkter uten prisjustering (eller med 0) avregnes ikke.

## Hvorfor avregnes ikke Altinn Studio applikasjonsinfrastruktur?

Applikasjonsinfrastruktur faktureres til bokført Azure-kostnad, uten påslag. Beløpet på fakturaen er derfor allerede lik kostnaden som er bokført (1:1), og det er ingen pris å justere i ettertid. Produktet står i tabellen med «Avregnes ikke», og forsystem avviser det også om noen prøver å sende det inn.

## Gjør slik

Du må ha rollen FORVALTER.

1. Åpne [Avregning](/avregning) og kontroller **Avregningsåret**. Bytt år med [Vis år](knapp:).
2. Kryss av for månedene som skal avregnes. Måneder uten importert bruksdata kan ikke velges.
3. Skriv prisjusteringen for hvert produkt som skal avregnes.
4. Trykk [Generer utkast til fakturakjøring](knapp:). Du kommer til kjøringen.
5. Kontroller funnene på fanen **Kontroller**, rett fakturateksten ved behov, og godkjenn.

Fakturateksten er «Avregning bruk av Altinn» etterfulgt av året, for eksempel «Avregning bruk av Altinn 2027». Du kan endre den som i en vanlig kjøring, se [Endre tekstene på fakturaen](endre-fakturatekster.md).

## Gode å vite

- Avregningen ligger utenfor månedskjøringene. Du kan ha en avregning for 2027 ved siden av kjøringen for januar 2027.
- Det kan bare finnes én avregning per år som venter på godkjenning eller eksport. Forkast den først for å lage en ny.
- Kundenavn, kundenummer og referanser hentes fra bruksdataene for de valgte månedene, den nyeste måneden først.
- Terskelverdien gjelder ikke for en avregning.
- En avregning med negativt beløp kan ikke eksporteres ennå. Hvordan kreditt skal leveres til Unit4, er ikke avklart (OQ-6).

## Hvis noe går galt

- **«Velg minst én måned å avregne»:** du krysset ikke av for noen måned.
- **«Legg inn prisjustering for minst ett produkt»:** alle feltene er tomme eller 0.
- **«Ingen bruk å avregne for produktene og månedene du valgte»:** ingen kunder har bruk av produktene i de valgte månedene.
- **«Det finnes allerede en avregning for …»:** forkast den eksisterende avregningen først.
