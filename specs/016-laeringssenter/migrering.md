# Dekningskart: den gamle brukerveiledningen → Læringssenteret

Bevis for specs/016 US3, scenario 1: hver del av `docs/brukerveiledning.md` (før den ble en henvisning)
finnes i en artikkel, eller er bevisst droppet. Artikkel-id-er er `seksjon/side` under
`src/main/resources/hjelp/`; innholdet er sjekket mot gjeldende maler og kontrollere, ikke bare flyttet.

| Gammel del | Nå |
|---|---|
| Innledning og «Kort fortalt» | `kom-i-gang/hva-er-forsystem` |
| 1. Det store bildet | `kom-i-gang/hva-er-forsystem`, `forsta-systemet/finmod-kort-fortalt` |
| 2. Roller | `kom-i-gang/roller-og-tilgang`, `oppslag/roller`, `nar-noe-gar-galt/ingen-tilgang` |
| 3. Navigasjon | `kom-i-gang/rundtur` |
| 4. Produkter | `slik-gjor-du/legg-inn-kontering`, `slik-gjor-du/koble-kildenavn`, `manedsrutinen/1-sjekk-registrene` |
| 5. Prisversjoner | `slik-gjor-du/lag-prisversjon`, `slik-gjor-du/endre-pris-eller-terskelverdi`, `forsta-systemet/prisversjon-og-gyldighet`, `forsta-systemet/terskelverdi`, `nar-noe-gar-galt/ingen-aktiv-prisversjon` |
| 6. Kunder | `slik-gjor-du/legg-til-fakturareferanse`, `slik-gjor-du/legg-til-kundenummerregel`, `slik-gjor-du/avvikende-fakturamottaker`, `forsta-systemet/slik-grupperes-fakturaer` |
| 7. Bruksdata | `manedsrutinen/2-hent-bruksdata`, `slik-gjor-du/last-opp-csv`, `slik-gjor-du/importer-periode-pa-nytt`, `slik-gjor-du/opprett-kunder-fra-fila`, `oppslag/csv-format`, `nar-noe-gar-galt/import-blokkeres`, `nar-noe-gar-galt/fila-kjennes-ikke-igjen`, `nar-noe-gar-galt/ingen-bruksdata` |
| 8. Kjøringer | `manedsrutinen/3-generer`, `manedsrutinen/5-godkjenn`, `manedsrutinen/6-eksporter`, `slik-gjor-du/endre-fakturatekster`, `slik-gjor-du/forkast-og-generer-pa-nytt`, `slik-gjor-du/sjekk-ut-advarsler`, `slik-gjor-du/finn-en-faktura`, `slik-gjor-du/last-ned-fakturagrunnlag`, `slik-gjor-du/kopier-epost-til-regnskap`, `slik-gjor-du/lag-korrigeringskjoring`, `forsta-systemet/korrigering-og-avregning`, `oppslag/filer`, `nar-noe-gar-galt/eksport-stoppet`, `nar-noe-gar-galt/eksportert-kan-ikke-forkastes` |
| 9. Hendelseslogg | `slik-gjor-du/hvem-endret-hva`, `slik-gjor-du/er-lg04-lastet-ned`, `forsta-systemet/sporbarhet` |
| 10. Hele månedsrutinen | `manedsrutinen/1-sjekk-registrene` … `6-eksporter` (og runbookens brukerrettede trinn) |
| 11. Kontrollfunn | `oppslag/kontrollfunn` (generert fra `Kontrollkode`) og `kontrollfunn.yml`; `manedsrutinen/4-kontrollfunn` |
| 12. Statusflyt | `oppslag/statuser` (blokkene `livslop-kjoring` og `livslop-prisversjon`) |
| 13. Feilsøking / FAQ | `nar-noe-gar-galt/*` (én artikkel per symptom, `oversikt` som register) |
| 14. Ordliste | `oppslag/ordliste` |

## Bevisst droppet eller endret

- **Kunder-menyen og kundelisten.** Fjernet fra appen 2026-11-11; ingen side lenker til kundesiden. Det finnes
  derfor ingen «Finn og åpne en kunde»-artikkel; den skrives når det er bestemt hvordan brukerne kommer til
  en kunde (spec, Assumptions). Kunde-artiklene sier ærlig fra om dette.
- **«Seks produkter».** Utdatert i den gamle veiledningen. Artiklene oppgir ikke et antall, men viser til
  kolonnen Kode på Produkter-siden (produktantallet er ikke avklart i kilden, se rapporten fra PR 2).
- **«LESER ser alt, FORVALTER kan i tillegg …».** Feil mot appen: rollene er ikke hierarkiske
  (GODKJENNER gir ikke FORVALTER). `oppslag/roller` følger `SecurityConfig`.
- **«Send filen til DFØ».** Erstattet etter OQ-8: filen sendes på e-post til regnskap i VIS (appen sender
  ikke selv). Se `manedsrutinen/6-eksporter`.
- **«Avregning er ikke bygget».** Utdatert: sammenligning og korrigeringskjøring finnes i appen. Eksport av
  korrigeringer er sperret i produksjon til OQ-6 er avklart; det står i korrigeringsartiklene.
- **Skjermbilder.** Finnes ikke i den gamle veiledningen heller, og er utenfor omfanget (spec).
