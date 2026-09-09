# 09 — Beslutningslogg og korrespondanse

Kronologisk oversikt over alle avklaringer, beslutninger og e-poster i prosjektet, fra
analysen av dagens løsning til ferdig MVP. Skrevet på norsk fordi dette er
presentasjons- og forvaltningsdokumentasjon; de tekniske dokumentene (01–08) er på engelsk.

## Korrespondanse (kronologisk)

### 2026-07-07 — Analyse av dagens løsning (`../agresso`)

Dybdeanalyse av dagens faktureringsverktøy (FEL). Hovedfunn: løsningen fungerer, men
er operativt manuell (psql-import av CSV, manuell kjøring per profil, iconv-konvertering,
manuell sending); forretningslogikken ligger i SQL-funksjoner som ikke er versjonert;
faktureringsperioden avledes av klokka; kontering og ansvarlige er hardkodet. Mest verdifulle
artefakt: LG04-skriveren med golden-file-tester som låser formatet tegn for tegn.

### 2026-07-07 — Spørsmålsmail sendt (8 temaer)

Omfang, datavarehus, CRM, Unit4-kanal, priser/produkter, kontroller/godkjenning/
detaljvisning, avregning, dokumentasjon.

### 2026-07-07 — «Løsningsbeskrivelse for fakturering» mottatt (fra prosjektleder)

Definerer målbildet: kildedata → datavarehus (avgjør *hva* som er fakturerbart) →
**forsystem** (avgjør *hvordan* det faktureres: henter kunder fra CRM, bruksdata fra
varehuset, benytter prisliste, utfører kontroller, sender fakturagrunnlag til Unit4) →
Unit4 → tjenesteeier (faktura + detaljvisning). Prinsipper: én autoritativ kilde per
dataelement; alt sporbart tilbake til bruksdata, avtaler og priser. Midlertidig
produkttabell i forsystemet. Åpne avklaringer med eiere: CRM↔Unit4-kobling (VIS), CRM som
autoritativ kilde (Økonomi og Kunde), avregning (Økonomi og VIS).

Samtidig: tilgang til FinMod dataelement-arket. Viktigste innhold: obligatoriske
dataelementer med mastere; Ivars forretningsregler (avvikende fakturamottaker,
produktspesifikke fakturareferanser, flere/delte kundenumre med servicekode/tilleggstekst,
flere fakturalinjer per kunde); BRREG-validering av orgnr/navn er dagens praksis;
kundedata ligger i dag i Excel (3 ark + websak).

### 2026-07-07 — Svar fra prosjektlederen (omfang, CRM, priser, PDF)

- **Omfang**: kun Altinn-produktene i denne fasen — melding, formidling, varsling,
  autorisasjon og Studio inkl. applikasjonsinfrastruktur. Prisene er publisert.
- **CRM**: Dynamics 365. Uavklart om CRM rekker første fakturering — møte 10. august.
  Uansett må vedlikeholdsprosesser etableres; midlertidig løsning om CRM ikke rekker det.
- **Priser/produkter**: ingen autoritativ produktkatalog kommer tidsnok — produkter og
  priser registreres og vedlikeholdes direkte i forsystemet. Prisjustering én gang i året
  (statsbudsjettet); prisene for kommende år settes i april året før.
- **Detaljvisning**: fortsatt PDF, samme linje som resten av Digdir; FEL-kontakten gjør dette som
  del av FEL-arbeidet.

### 2026-07-08 — Svar fra prosjektlederen (tidslinje)

Den nye prismodellen gjelder **fra 1. januar 2027** — bruksvolum og Azure-kostnader inngår
fra denne datoen. 1.1.2027 = oppstart måling og datainnsamling; **begynnelsen av februar
2027 = første kjøring av forsystemet og første fakturering**, basert på faktisk bruk i
januar. Overføring til Unit4 følger dagens FEL-rutiner; FEL-kontakten svarer på tekniske detaljer.

### 2026-07-08 — Vår plan-mail (MVP) + infrastruktursak

MVP speiler dagens FEL-rutiner i skyen: registre i applikasjonen (i stedet for Excel),
bruksdata via filopplasting inntil DWH-kontrakt finnes, generering med kontroller og
manuelt godkjenningssteg, LG04 og PDF lastes ned og sendes manuelt. Kundedata-delen
flagget eksplisitt som *vår tolkning* av den midlertidige løsningen, til bekreftelse.
Infrastruktursak opprettet: [altinn-platform#3809](https://github.com/Altinn/altinn-platform/issues/3809)
(testmiljø september, produksjon november).

### 2026-07-08 — Svar fra DVH-kontakten (datavarehus, CRM)

- Datavarehuset er PostgreSQL-basert og heter **finopsdevsa** («BOD» i diagrammet er
  feil navn). Bygget/driftet av plattformteamet. Innhenting: Logic App → Blob → Azure
  Data Factory → PostgreSQL.
- Datagrunnlag som **materialiserte views** + Data API Builder-endepunkt
  (`…/api/mv_altinn_usage_monthly`). Sikkerhetslag uavklart (FEL↔plattform, pauset pga.
  ferie).
- **Komplett fakturerbart grunnlag den 6. i hver måned** (Azure-kostnader er endelige
  fem dager ut i påfølgende måned).
- Tilgang søkes via platform-bordet ([Altinn/projects/117](https://github.com/orgs/Altinn/projects/117)).
  Historikk varierer per produkt (Altinn 2→3-overgangen); suksesskriterium: fakturerbar
  historikk fra 1.1.2027.
- CRM bekreftet: digdir.crm4.dynamics.com; integrasjonsgrensesnitt ikke kartlagt.
  Referansetabellene som allerede vedlikeholdes i varehuset kan brukes som midlertidig
  kilde for fakturareferanser — eierskap for oppdatering er ubesluttet.

### 2026-07-08 — Svar fra FEL-kontakten (leveranse til Unit4)

LG04-fila sendes **per e-post til regnskap i VIS**. E-posten angir periode, antall
fakturaer og samlet beløp inkl. mva. Regnskap importerer i Unit4; importen gir en rapport
(antall, beløp, gyldighet av kundenumre). Er alt korrekt, sendes fakturaene som
**EHF-faktura**. (Reiste nytt spørsmål: mva-behandling, OQ-11. Fortsatt ubesvart: flere
beløpslinjer per ordre, OQ-1.)

### 2026-08-18 — Oppdatert løsningsbeskrivelse (fra prosjektleder)

Utvidet versjon med logisk løsningsarkitektur, komponentbeskrivelser, tabell over
autoritative kilder, midlertidige løsninger og en liste over gjenstående avklaringer med
ansvarlige. Bekrefter det vi har bygget på, og legger til flere konkrete fakta:

- **Autoritative kilder** som tabell: kunde/avtale = CRM, bruks- og kostnadsdata =
  datavarehus, **priser og produkter = forsystemet (produkter midlertidig)**,
  **kundenummer og artikkel-ID = Unit4**, faktura/bokføring = Unit4.
- **Kundenummerflyt**: nye kunder må først opprettes i Unit4 for å få kundenummer, som
  deretter registreres i CRM. Kundenummer skal ikke endres i CRM uten grunnlag i Unit4.
- **Artikkel-ID**: opprettes i Unit4 og *registreres* i forsystemet. Regnskap oppretter
  nødvendige artikkelnumre via DFØ **innen desember 2026**; foreløpig vurdering er **unikt
  artikkelnummer per Altinn-produkt** — som er nøyaktig `produkt.artikkel_id`. Endelig
  behov avklares av økonomi som del av konteringsarbeidet for FinMod (OQ-2).
- **Kundenummerkontroll**: forsystemet kontrollerer kundenummer før grunnlaget sendes
  videre; ved LG04-import markeres kundenummer som **mangler, er ukjent, feil eller
  deaktivert**. De tre siste krever Unit4s kundeliste — utenfor forsystemets rekkevidde.
- **Datavarehuset** kobler bruk til riktig tjenesteeier, kontrollerer mot aktive avtaler og
  filtrerer bort ikke-fakturerbar bruk. Vår INAKTIV_AVTALE-kontroll er dermed andre nett,
  ikke første.
- **Nye gjenstående avklaringer** med eiere: avregning (Økonomi og VIS), bokføring
  (Økonomi og VIS), kundenummer Unit4↔CRM (VIS, økonomi, kunde), datavalidering i CRM
  (Kunde og Økonomi) og **kobling mellom bruk og kunde** (Datavarehus og kunde) — sistnevnte
  er ny for oss (OQ-12).

### 2026-08-18 — CRM-tråd: felter og datavalidering (prosjektleder, Økonomi, CRM-forvalter, FEL)

CRM-forvalteren har lagt til rette for registrering av fakturainformasjon på
**Altinn-integrasjonen i CRM (testmiljø)**: organisasjonsnummer, fakturaadresse/
fakturamottaker, referanse, kundens PO-/ordrenummer, kundenummer i Unit4 og avtalestatus.
Økonomi ble bedt om å bekrefte at feltene dekker behovet. Utfallet av tråden:

- **Feltnavn**: «kundens PO-/ordrenummer» heter **Bestillingsnummer** i Unit4 og i FinMod-
  arket (Økonomi) — samme navn som vår `bestillingsnummer`-kolonne.
- **Organisasjonsnummer**: 9 siffer (Økonomi). I CRM er feltet i dag fritekst (100 tegn) og
  påkrevd ved opprettelse av virksomhet; 9-sifferregel er *ikke* satt, «blant anna pga
  utanlandske virksomheter» (CRM-forvalteren) → nytt spørsmål OQ-14, siden vår modell
  krever 9 siffer og BRREG-treff.
- **Kundenummer**: **4 tegn** (FEL: «kan låses til dette antall»; Økonomi: «4 siffer»).
  Feltet er nytt i CRM, ikke obligatorisk (ikke alle rader skal ha det), og kan låses for
  skriving med endringsrett til utvalgte personer siden Unit4 er autoritativ kilde.
- **Obligatoriske felter**: alle unntatt bestillingsnummer bør være obligatoriske før
  fakturering (Økonomi), men kundeteamet må svare på om det hindrer Servicedesk. CRM kan
  sette betinget krav, f.eks. obligatorisk når type = Altinn (CRM-forvalteren) → OQ-15.
- **Avtalestatus**: kun definerte verdier — i CRM **aktiv eller inaktiv** (CRM-forvalteren).
  Økonomi påpeker at semantikken må avklares: hvem som helst kan signere bruksvilkår, men
  ikke alle kan være tjenesteeier → OQ-13.
- **Fakturaadresse/fakturamottaker**: noen av feltene inneholder **annet enn et
  organisasjonsnummer** (CRM-forvalteren) → relevant for OQ-7; vi legger ingen formatregel på
  `fakturamottaker_orgnr`.
- **Kundenummer i Unit4** kontrolleres i eget løp, blant annet om ett organisasjonsnummer
  kan være knyttet til flere kundenumre (prosjektleder) → OQ-10.
- **Kundeteamet** laster inn korrekt fakturainformasjon for Altinn-kundene i CRM
  når feltene er avklart.

Viktigste konsekvens: den avtalte kortsiktige løsningen for kundedata ligger **i CRM**, ikke
i forsystemet (B-14). OQ-5 er dermed besvart på *kilde*; grensesnittet CRM→forsystem er
fortsatt ukartlagt.

## Beslutninger

| # | Beslutning | Kilde / dato | Konsekvens |
|---|---|---|---|
| B-01 | Dagens løsning røres ikke; nytt system bygges i eget repo, `../agresso` er kun lesereferanse | Oss, 2026-07-07 | FEL fortsetter uforstyrret; LG04-skriver + golden-tester gjenbrukes |
| B-02 | Omfang fase 1: kun Altinn-produktene under ny prismodell | prosjektleder, 2026-07-07 | dp/e-signering/gammel Altinn/BOD blir på gammel løsning |
| B-03 | Produkter og priser er forsystemets ansvar (midlertidig master), med versjonert prisliste | Løsningsbeskrivelse + prosjektlederen | Register-UI med UTKAST→AKTIV→ARKIVERT-livssyklus |
| B-04 | Månedlig fakturering basert på faktisk bruk; første kjøring februar 2027 | prosjektleder, 2026-07-08 | Tidsplan: dry-run desember, måling fra 1.1.2027 |
| B-05 | Kjøring tidligst den 6. i måneden (Azure-kostnader endelige) | DVH-kontakt, 2026-07-08 | Premiss for månedsrutinen og fremtidig scheduler |
| B-06 | MVP: manuell sending (e-post til regnskap i VIS), CSV-opplasting, PDF-nedlasting | Oss (bekreftet av FEL-kontaktens rutinebeskrivelse) | Ingen leveranseautomatikk/kvitteringsinnlesing i MVP |
| B-07 | Stack: Java 21, Spring Boot, PostgreSQL 16 + Flyway, Spring Data JDBC, Thymeleaf + htmx, Designsystemet | Oss, docs/02 | Gjenbruk av LG04-kode; én deploybar enhet; skjema versjonert |
| B-08 | Azure-klar, ikke Azure-låst: porter/adaptere (UsageDataSource, CustomerSource, FileArchive, BrregOppslag), OIDC, 12-faktor-konfig, alt kjører offline med docker compose | Oss, docs/02 | Ingen Azure-SDK i kjernen; miljøvalg er en driftsbeslutning |
| B-09 | Faktura grupperes per (kunde, kundenummer); ordre_nr ligger på faktura | Oss, docs/03 (av Ivars regler) | Ivars spesialtilfeller håndteres; OQ-1-sikker eksport |
| B-10 | Full sporbarhet som struktur: linje → bruksdata + pris; kjøring → prisversjon; snapshots ved generering | Løsningsbeskrivelsens prinsipp | Historikk skrives aldri om; revisjon mulig |
| B-11 | Eksport: én LG04-ordre per linje inntil OQ-1 er avklart (trygt standardvalg) | Oss | Kan slås om med flagg uten datamodellendring |
| B-12 | BRREG-validering ved registrering av kunde (ikke-eksisterende orgnr blokkerer; navneavvik varsler) | FinMod-arket («valideres mot BRREG i dag») | Lukket det ene stille gapet i kravsporingen (K-16) |
| B-13 | Ingen mva-beregning før OQ-11 er avklart; sammendrag viser filbeløp merket «mva uavklart» | Oss, etter FEL-kontaktens svar | Unngår gale tall i e-posten til regnskap |
| B-14 | CRM er den avtalte kortsiktige løsningen for kunde- og avtaleinformasjon; forsystemets kunderegister er cache, dry-run-kilde og reserveløsning — ikke midlertidig master | Løsningsbeskrivelsen + CRM-tråden, 2026-08-18 | OQ-5 besvart på kilde. Register-UI beholdes uendret (`kilde='MANUELL'`), men import fra CRM blir hovedveien (docs/06 §2). Gjenstår: grensesnitt og tidspunkt |

## Antakelser til bekreftelse

| Antakelse | Status |
|---|---|
| ~~Forsystemets register-UI er den midlertidige løsningen for kundedata~~ | **Avkreftet 2026-08-18**: den kortsiktige løsningen ligger i CRM (B-14). Registeret er cache/fallback |
| Kundenummer er 4 tegn og bør valideres som det i forsystemet | Faktum bekreftet (FEL/Økonomi); ikke implementert — kolliderer med demodataenes tydelige fakes (`KN-1001`) |
| Alle Altinn-tjenesteeiere har 9-sifret norsk organisasjonsnummer | Til bekreftelse (OQ-14); CRM åpner for utenlandske virksomheter |
| LG04 støtter i praksis dagens format uendret for nye produkter | Verifiseres i test-import med regnskap i desember |
| Produktkodene våre matcher navngivingen i `mv_altinn_usage_monthly` | Verifiseres når kolonnekontrakten mottas (OQ-4) |
