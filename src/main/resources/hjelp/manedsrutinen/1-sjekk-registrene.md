---
tittel: Sjekk registrene
sammendrag: Før du henter bruk, sjekker du at prisversjon, kontering og kildenavn er på plass, slik at kjøringen ikke stopper på kontrollene.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /prisversjoner
apne-i-appen-tekst: Åpne Prisversjoner
relatert: [slik-gjor-du/lag-prisversjon, slik-gjor-du/legg-inn-kontering, slik-gjor-du/koble-kildenavn, forsta-systemet/prisversjon-og-gyldighet, manedsrutinen/2-hent-bruksdata]
nokkelord: [registre, forberedelse, prisliste, kontering, kildenavn, avtalestatus]
---
Fakturering skjer én gang i måneden, for forrige måned. Dette er første steg, og det tar vanligvis noen minutter. Registrene du sjekker her, avgjør om kjøringen går gjennom eller stopper på blokkerende [kontrollfunn](../oppslag/kontrollfunn.md).

## Hvem

Du trenger rollen FORVALTER for å endre registrene. Med rollen LESER kan du se dem, men ikke endre noe. Har du ikke rett rolle, se [Roller og tilgang](../kom-i-gang/roller-og-tilgang.md).

## Før du starter

Bestem hvilken måned du skal fakturere. Perioden er alltid den første dagen i måneden som har vært målt, for eksempel januar når du fakturerer i februar.

## Gjør slik

1. Åpne [Prisversjoner](/prisversjoner). Sjekk at det finnes en versjon med status **AKTIV** som dekker periodens første dag. En versjon som starter midt i en måned, brukes først fra måneden etter. Mangler den, lag og aktiver en ny. Se [Lag prisversjon](../slik-gjor-du/lag-prisversjon.md) og [Prisversjon og gyldighet](../forsta-systemet/prisversjon-og-gyldighet.md).
2. Åpne den aktive versjonen og kontroller at alle aktive produkter har en enhetspris, og at terskelverdien er den du forventer. Både priser og terskelverdi er låst når versjonen er aktiv. Skal noe endres, må det lages en ny versjon. Se [Endre pris eller terskelverdi](../slik-gjor-du/endre-pris-eller-terskelverdi.md).
3. Åpne [Produkter](/produkter). Alle produkter som skal faktureres, må ha kontering: konto, dimensjoner og artikkel-id. Fyll ut det som mangler, med kodene du har fått fra Økonomi. Se [Legg inn kontering](../slik-gjor-du/legg-inn-kontering.md).
4. Åpne [Kildenavn](/produkter/kildenavn) og sjekk at produktnavnene datavarehuset bruker, er koblet til riktig produkt. Et ukjent navn gjør at importen i neste steg avvises. Se [Koble kildenavn](../slik-gjor-du/koble-kildenavn.md).
5. Sjekk kundene. Kunden må ha avtalestatus AKTIV, en kundenummerregel som passer produktene, og helst en fakturareferanse. Det finnes ikke en egen Kunder-meny i forsystem. Kunder som mangler, kan opprettes fra bruksfila i [steg 2](2-hent-bruksdata.md). For andre endringer, se artiklene under Kunder i [Slik gjør du](../slik-gjor-du/legg-til-fakturareferanse.md), eller ta kontakt med forsystem-teamet.

> [!IMPORTANT]
> Hvilke kontoer og dimensjoner som skal stå på hvert produkt, er ikke endelig avklart (OQ-2). Fyll bare inn koder du har fått fra Økonomi. Står kontoen tom, stopper kontrollen deg med `MANGLER_KONTERING`. Det er bedre enn å fakturere med feil kode.

## Slik ser du at det gikk bra

- Prisversjonen står som **AKTIV**, og teksten på siden sier at den brukes for kjøringer fra måneden du skal fakturere.
- Produktlisten viser verdier i kolonnene for konto, dimensjoner og artikkel-id, ikke «—», for alle aktive produkter du skal fakturere.
- Produktnavnene fra datavarehuset er koblet under Kildenavn.

Fortsett til [Hent bruksdata](2-hent-bruksdata.md).

## Hvis noe går galt

- **Du kan ikke aktivere prisversjonen.** Enten mangler et aktivt produkt pris, eller en annen aktiv versjon overlapper datoene. Meldingen på siden sier hvilket.
- **Du ser ikke Lagre eller Aktiver.** Du har trolig bare rollen LESER, eller versjonen er allerede aktiv og dermed låst.
- **Du vet ikke hvilken konto produktet skal ha.** La feltet stå tomt og spør Økonomi. Ikke gjett.
