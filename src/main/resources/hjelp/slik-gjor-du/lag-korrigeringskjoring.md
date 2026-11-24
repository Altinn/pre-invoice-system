---
tittel: Lag en korrigeringskjøring
sammendrag: Slik sammenligner du ny bruksdata med det som allerede er eksportert, velger endringene og lager en korrigeringskjøring.
oppdatert: 2026-11-24
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [forsta-systemet/korrigering-og-avregning, slik-gjor-du/forkast-og-generer-pa-nytt, slik-gjor-du/last-opp-csv]
nokkelord: [korrigering, V2, avregning, endret bruksdata, sammenligning, differanse, kunde, fakturareferanse, bestillingsnummer]
---
Er bruksdata for en måned endret etter at kjøringen ble eksportert, kan du lage en korrigeringskjøring som bare inneholder differansene. Den første kjøringen blir stående uendret.

> [!WARNING]
> Du kan lage, kontrollere og godkjenne en korrigeringskjøring, men ikke eksportere den i produksjon. Hvordan negative korrigeringer skal leveres til Unit4, er ikke avklart (OQ-6). Les [Korrigering og avregning](../forsta-systemet/korrigering-og-avregning.md) før du starter.

## Før du starter

- Måneden har en kjøring med status «Eksportert». Er kjøringen ikke eksportert, bruker du i stedet [Forkast og generer på nytt](forkast-og-generer-pa-nytt.md).
- Du har rollen FORVALTER.
- Du har en komplett ny bruksdatafil for måneden fra datavarehuset. Forsystems eget CSV-format kan ikke brukes til korrigering.
- Det finnes ingen kjøring for måneden som venter på godkjenning eller eksport. Forkast eller eksporter den først.

## Gjør slik

1. Åpne [Bruksdata](/bruksdata).
2. Hent måneden på nytt med [Hent fra datavarehus](knapp:) (finnes når datavarehuset er koblet til), eller last opp datavarehusets eksport og trykk [Forhåndsvis](knapp:). Velg samme periode som den eksporterte kjøringen.
3. Fordi måneden allerede er eksportert, havner du på siden **Endringer i bruksdata**. Du finner den også igjen med [Sammenligning](knapp:) på raden i tabellen **Importer**.
4. Se på tabellen **Avvik per kunde**. Den har én rad per kunde med avvik: navn, organisasjonsnummer, U4 kundenummer, hva som er endret, antall produkter og differansen i kroner. Kunder uten avvik er skjult. Trykk [+](knapp:) foran navnet for å se produktene, med verdi før, verdi nå og differanse.
5. Se på merkene i kolonnen **Endring**:
   - **Lagt til**, **Endret** og **Fjernet** gjelder bruken av et produkt.
   - **Endret fakturareferanse**, **Endret bestillingsnummer** og **Endret kundenummer** betyr at CRM har en annen verdi enn den kunden ble fakturert med. Gammel og ny verdi står under navnet. Differansen kan være 0.
   - **Endret navn** vises, men navnet står ikke i fakturafila.
6. Bruk filtrene Kunde eller orgnr, Kundenummer, Produkt, Endring og Differanse, og trykk [Filtrer](knapp:).
7. Kryss av kundene som skal med, eller trykk [Velg alle](knapp:). Ingenting er valgt på forhånd. En kunde tas med med alle produktene sine. En kunde med «Kan ikke tas med ennå» har endret referanse eller kundenummer og kan ikke krysses av (se under). Er det flere sider, velger [Velg alle](knapp:) kundene på alle sidene.
8. Skriv en begrunnelse i **Hvorfor opprettes korrigeringen?**. Den er påkrevd.
9. Trykk [Opprett korrigering V2](knapp:). Versjonsnummeret øker for hver korrigering.

## Slik ser du at det gikk bra

Du kommer til en ny kjøring som heter «Korrigering» med måned og versjon. Øverst står begrunnelsen, med lenker til forrige versjon og de valgte endringene. Kjøringen følger de samme kontrollene og trinnene som en vanlig kjøring: [sjekk kontrollfunn](../oppslag/kontrollfunn.md), sjekk ut advarsler, godkjenn. Fakturalinjene har fortegn, så en reduksjon blir et negativt beløp.

Korrigeringen bruker prisene fra den opprinnelige kjøringen, ikke en nyere prisversjon. Terskelverdien gjelder ikke.

## Hvis noe går galt

- **Du kommer ikke til sammenligningen, men får forhåndsvisning:** måneden har ingen eksportert kjøring. Da er det en vanlig import.
- **«Korrigering krever en komplett eksport fra datavarehuset»:** du lastet opp forsystems eget CSV-format. Bruk datavarehusets eksport eller hent fra datavarehuset.
- **«Grunnlaget er endret siden sammenligningen ble åpnet. Sammenlign på nytt»:** gjør sammenligningen på nytt og velg igjen.
- **«Det finnes allerede en pågående kjøring»:** forkast eller eksporter den først.
- **«Endret fakturareferanse, bestillingsnummer eller kundenummer kan ikke korrigeres med differanser …»:** en av kundene du krysset av, har fått ny referanse eller nytt kundenummer i CRM. En korrigering med differanser ville fått den nye verdien, mens fakturaen den retter, hadde den gamle. Hvordan en faktura skal krediteres og sendes på nytt, er ikke avklart (OQ-6). Fjern krysset for kundene som er nevnt i meldingen. Se [Korrigering og avregning](../forsta-systemet/korrigering-og-avregning.md).
- **«Disse kundene har ingen endring i bruk som kan korrigeres»:** kunden har bare fått nytt navn. Navnet står ikke i fakturafila, så det er ingenting å korrigere. Fjern krysset.
- **Eksporter-knappen mangler:** i stedet står det at korrigeringen ikke kan eksporteres før avregningsmåten er avklart og testet med regnskap (OQ-6). Det er forventet i produksjon.
- **Du valgte feil:** [Forkast](knapp:) korrigeringen. Sammenligningen åpnes igjen, og et nytt utkast får neste versjonsnummer.
