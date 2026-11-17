---
tittel: Legg inn fakturareferanse for en kunde
sammendrag: Slik registrerer du fakturareferanse og bestillingsnummer, standard eller per produkt, innenfor grensen på 25 tegn.
oppdatert: 2026-11-17
rolle: FORVALTER
relatert: [slik-gjor-du/legg-til-kundenummerregel, slik-gjor-du/endre-fakturatekster, oppslag/kontrollfunn, forsta-systemet/slik-grupperes-fakturaer]
nokkelord: [Deres referanse, MANGLER_FAKTURAREFERANSE, DELT_REFERANSE, bestillingsnummer, kundesiden]
---
Fakturareferanse (på fakturaen kalt «Deres referanse») og bestillingsnummer registreres per kunde, og de kopieres til fakturalinjene når du genererer en kjøring. Du trenger rollen FORVALTER for å legge dem inn eller slette dem.

> [!IMPORTANT]
> Kundesiden har ingen lenke i menyene i dag. Forvaltningen åpner den med en direkte lenke de har fått. Mangler du lenken, spør forvaltningen. Hvis kunden ikke finnes ennå, kan den opprettes fra en datavarehusfil, se [Opprett kunder fra fila](opprett-kunder-fra-fila.md). Da får kunden fakturareferanse og bestillingsnummer slik datavarehuset har dem.

## Gjør slik

1. Åpne kundesiden. Under overskriften **Fakturareferanser** ser du tabellen med det som er registrert.
2. I skjemaet under tabellen velger du **Produkt**:
   - **(standard)** gjelder for kunden uansett produkt.
   - Et bestemt produkt gjelder bare for det produktet og går foran standarden.
3. Skriv **Fakturareferanse**.
4. Skriv **Bestillingsnummer**, eller la feltet stå tomt.
5. Trykk [Legg til](knapp:).

Vil du fjerne en rad, trykker du [Slett](knapp:) på raden. Det finnes ingen redigering. Slett raden og legg den inn på nytt.

## Grensen på 25 tegn

Både fakturareferanse og bestillingsnummer kan være **høyst 25 tegn**. Feltene stopper deg i skjemaet. Hvis noe likevel kommer gjennom, avviser forsystem raden med en melding som «Fakturareferanse kan være høyst 25 tegn (har 31)». Mellomrom i begynnelsen og slutten telles ikke. Korte inn referansen og prøv igjen.

Unit4 tar selv imot lengre bestillingsnummer (opptil 100 tegn). Grensen på 25 følger FEL sin praksis, fordi de to feltene som regel har samme verdi. Om de skal ha ulike grenser, er ikke avklart (OQ-23).

## Står bestillingsnummer tomt?

Da får fakturaen fakturareferansen som bestillingsnummer. I tabellen står det «— (fakturareferansen brukes)». Det skjer når kjøringen genereres, og registeret endres ikke. Retter du fakturareferansen senere, følger bestillingsnummeret med neste gang du genererer.

## Standard og produktrad

- En produktrad **erstatter** standarden for det produktet, også hvis fakturareferansen på produktraden er tom. Produktet får da ingen fakturareferanse. Om en tom produktrad heller skal arve standarden, er ikke avklart (OQ-24).
- Har kunden ulike referanser per produkt, deles fakturaen i flere i Unit4 (funnet `DELT_REFERANSE`). Det er en advarsel, ikke en feil.
- Mangler en linje fakturareferanse, får fakturaen advarselen `MANGLER_FAKTURAREFERANSE`. Advarsler hindrer ikke godkjenning, men de må gås gjennom først. Legg gjerne inn referansen før du genererer. Se [Kontrollfunn](../oppslag/kontrollfunn.md) og [Sjekk ut advarsler](sjekk-ut-advarsler.md).

## Slik ser du at det gikk bra

Raden står i tabellen med riktig produkt. Generer kjøringen på nytt, så er referansen med på fakturalinjene. Endringen er logget i [Hendelseslogg](/hendelseslogg).
