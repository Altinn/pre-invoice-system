# Innspill til DWH-teamet: `mv_altinn_usage_monthly` i Data API Builder

Vi i forsystemet (fakturering av Altinn-produkter under FinMod) skal hente bruksdata fra
`https://finops-dab-api.wonderfulriver-01d57917.norwayeast.azurecontainerapps.io/api/mv_altinn_usage_monthly`
i stedet for manuell CSV-opplasting. Vi testet API-et 6. oktober 2026 og fant noen ting vi trenger
hjelp med før vi kan bruke det i produksjon. Punkt 1 og 2 er de viktigste.

## 1. API-et er åpent uten autentisering

Hvem som helst på internett kan i dag hente organisasjonsnummer, kundenummer og fakturareferanse for
alle tjenesteeiere med et vanlig `GET`-kall. Dette er fakturagrunnlag og bør ikke være offentlig.

**Ønske:** Slå på Entra ID-autentisering i DAB (`authentication.provider: EntraID`) og gi
forsystemets identitet (workload identity / app-registrering) en rolle med lesetilgang til
entiteten. Vi trenger å vite hvilken `scope`/audience vi skal be om token for.

## 2. Paginering mister rader uten feilmelding

Entiteten har `organisation_number` som eneste nøkkel, men viewet har én rad per organisasjon ×
produkt × dag. DAB bygger `$after`-pekeren fra nøkkelen, så rader hoppes over når man blar:

- Med `$first=500` og `nextLink` fikk vi **2 445 av 2 519 rader – 74 rader manglet**, uten feil.
- `GET …/mv_altinn_usage_monthly/organisation_number/840747972` returnerer **1 rad**, mens
  organisasjonen har **59** rader i september.

For fakturering betyr dette at noen kunder ville blitt fakturert for lite uten at noen merket det.

**Ønske:** Sett `key-fields` på entiteten i DAB-konfigurasjonen til
(`organisation_number`, `product_name`, `transaction_date`), eventuelt legg til en egen unik
nøkkelkolonne i viewet. Inntil dette er fikset henter vi hele måneden i ett kall og avviser svaret
hvis det inneholder `nextLink`.

## 3. `nextLink` peker til `http://`, ikke `https://`

Neste-side-lenken kommer tilbake som `http://finops-dab-api…`. DAB ser ikke at den står bak en
proxy som terminerer TLS. **Ønske:** Sett opp videresendte headere (`X-Forwarded-Proto`) eller
tilsvarende slik at lenken blir `https`.

## 4. Datofilter må skrives som tidspunkt

`$filter=transaction_date ge '2026-09-01'` gir HTTP 400 (DateTimeOffset mot streng); det fungerer
med `transaction_date ge 2026-09-01T00:00:00Z`. Ikke en feil, men greit å dokumentere. Svaret har
heller ingen fast sortering uten `$orderby`.

## 5. Hva viewet faktisk inneholder

Vi vil gjerne ha bekreftet:

- **Kornighet:** Navnet sier «monthly», men radene er per dag. Er det tilsiktet, og blir det
  slik fremover?
- **Historikk:** 6. oktober inneholdt viewet bare september 2026. Hvor lang historikk beholdes? Kan
  vi hente en tidligere måned på nytt (f.eks. ved korrigering)?
- **Oppfriskning:** Når oppdateres det materialiserte viewet, og er en måned endelig fra den 6. i
  påfølgende måned? Kan en avsluttet måned fortsatt endre seg etter det?
- **Produktnavn:** `product_name` er visningsnavn (`Autorisasjon`, `Altinn studio`,
  `Varsling e-post`, `Formidling`). Innsiktsviewene viser i tillegg `Melding`,
  `Varsling SMS (Norge)`, `Varsling SMS (utland)` og `altinn-studio-applikasjonsinfrastruktur`.
  Er disse navnene stabile, og hvilke vil komme i fakturaviewet? Hva er forskjellen på
  `Altinn studio` og `altinn-studio-applikasjonsinfrastruktur`?
- **Enhet for `quantity`:** Autorisasjon har opptil 11,2 millioner per dag for én organisasjon. Er
  `quantity` fakturerbar enhet eller rå antall kall?
- **`amount_nok` og `bestillingsnummer`** er alltid tomme. Er de planlagt fylt ut (f.eks. SMS- og
  Azure-kostnader i `amount_nok`)?
- **`kundenummer` og `fakturareferanse`:** Hvor kommer disse fra (CRM?), og hvordan holdes de i
  synk med kunderegisteret? 30 rader mangler fakturareferanse, og én har verdien
  `timefakturering: 1100eol`. Forsystemet vil bruke kunderegisteret som fasit og kun bruke deres
  verdier som kontroll.

## 6. Testmiljø

**Ønske:** Et endepunkt for test/tt02 med testdata (ikke produksjonsdata), slik at vi kan teste
integrasjonen uten å hente ekte fakturagrunnlag.

---

Kontakt: Adi Dahl (forsystemet). Tidsfrist: første produksjonskjøring tidlig februar 2027, med
generalprøve i desember 2026.
