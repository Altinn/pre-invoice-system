---
tittel: Generer kjøringen
sammendrag: Du genererer fakturagrunnlaget for måneden, sjekker tallene og tekstene på fakturaen, og laster ned grunnlaget for stikkprøver.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /kjoringer
apne-i-appen-tekst: Åpne Kjøringer
relatert: [manedsrutinen/2-hent-bruksdata, manedsrutinen/4-kontrollfunn, slik-gjor-du/endre-fakturatekster, slik-gjor-du/last-ned-fakturagrunnlag, slik-gjor-du/forkast-og-generer-pa-nytt, oppslag/statuser, forsta-systemet/slik-grupperes-fakturaer]
nokkelord: [generer, fakturakjøring, fakturagrunnlag, tekster, topptekst, bunntekst, vår referanse, avstemming]
---
En kjøring er én faktureringsrunde for én periode. Når du genererer, bygger forsystemet fakturaene fra bruksdataene og den aktive prisversjonen, og kjører kontrollene.

## Hvem

Du trenger rollen FORVALTER for å generere og for å endre tekstene på fakturaen. Med LESER kan du se kjøringen.

## Før du starter

- Bruksdata for perioden er [hentet og bekreftet](2-hent-bruksdata.md).
- Det finnes en aktiv prisversjon som dekker perioden.
- Det finnes ikke en annen kjøring for perioden som ikke er forkastet. En periode har bare én aktiv kjøring om gangen.

## Gjør slik

1. Åpne [Kjøringer](/kjoringer). Velg **Periode** under Ny kjøring og trykk [Generer](knapp:).
2. Åpne kjøringen med [Åpne](knapp:). Kjøringen får status «Venter på godkjenning», og siden viser «Neste steg: kontroller og godkjenn».
3. Les **Sammendrag** på fanen [Oversikt](knapp:): antall fakturaer, antall LG04-ordrer, antall linjer, og sum eksl. og inkl. mva. Mva er 25 % og regnes per faktura, så summen kan avvike med noen øre fra andre rapporter.
4. Åpne fanen [Fakturaer](knapp:) og se gjennom listen. Trykk [Linjer](knapp:) på en faktura for å se linjene. Kundene som ikke får faktura fordi de er under terskelverdien, står i listen «Faktureres ikke (under terskelverdi)». Se [Slik grupperes fakturaer](../forsta-systemet/slik-grupperes-fakturaer.md).
5. Åpne fanen [Kontroller](knapp:). Under Avstemming bruksdata ser du om all importert bruk kom med, og årsaken for hver rad som ikke kom med.
6. Last ned fakturagrunnlaget fra fanen [Filer](knapp:) hvis du vil ta stikkprøver i Excel. Det går også før godkjenning, og filnavnet viser statusen. Se [Last ned fakturagrunnlag](../slik-gjor-du/last-ned-fakturagrunnlag.md).
7. Sjekk **Tekster på fakturaen** på Oversikt: Topptekst, Bunntekst og Vår referanse. De er fylt ut på forhånd. Vil Økonomi ha noe annet denne måneden, endrer du dem og trykker [Lagre tekstene](knapp:). Se [Endre fakturatekster](../slik-gjor-du/endre-fakturatekster.md).

> [!IMPORTANT]
> Tekstene låses når kjøringen godkjennes. Vil du endre dem etterpå, må kjøringen forkastes og genereres på nytt, og da starter tekstene på standardverdiene igjen.

Her er livsløpet til en kjøring:

{{livslop-kjoring}}

## Slik ser du at det gikk bra

Kjøringen har status «Venter på godkjenning», og Sammendrag har tall som ser fornuftige ut. Ved siden av fanen Kontroller står eventuelle blokkerende funn eller advarsler som tall. Fortsett til [Gå gjennom kontrollfunn](4-kontrollfunn.md).

## Hvis noe går galt

- **«Ingen bruksdata for perioden — importer bruk først.»** Gå tilbake til [Hent bruksdata](2-hent-bruksdata.md).
- **«Ingen aktiv prisversjon dekker perioden.»** Aktiver en prisversjon som dekker måneden. Se [Ingen aktiv prisversjon](../nar-noe-gar-galt/ingen-aktiv-prisversjon.md).
- **«Det finnes allerede en aktiv kjøring for perioden.»** Forkast den gamle først. Se [Forkast og generer på nytt](../slik-gjor-du/forkast-og-generer-pa-nytt.md).
- **Teksten avvises ved lagring.** Teksten er for lang, har linjeskift eller har tegn LG04-fila ikke kan lagre, for eksempel emoji. Det du skrev, blir stående i skjemaet, så du kan rette det.
- **Teksten lagres, men siden advarer om at den kan bli for lang på trykt faktura.** Hvilken grense som gjelder på den trykte fakturaen, er ikke avklart (OQ-23).
- **Du vil teste uten å bruke en ekte måned.** I testmiljøet tt02 og lokalt finnes testverktøyet Nullstill periode nederst på Kjøringer. Det finnes ikke i produksjon.
