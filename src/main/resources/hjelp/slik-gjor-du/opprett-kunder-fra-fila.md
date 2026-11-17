---
tittel: Opprett manglende kunder fra en datavarehusfil
sammendrag: Slik registrerer du organisasjoner som har bruk i fila, men ikke finnes i kunderegisteret, med ett knappetrykk.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /bruksdata
apne-i-appen-tekst: Åpne Bruksdata
relatert: [slik-gjor-du/last-opp-csv, slik-gjor-du/legg-til-kundenummerregel, slik-gjor-du/legg-til-fakturareferanse, oppslag/kontrollfunn]
nokkelord: [MANGLER_KUNDE, ny kunde, kunderegister, datavarehus, eksport]
---
Har fila bruk for en organisasjon som ikke er i kunderegisteret, får du et blokkerende funn (`MANGLER_KUNDE`) når kjøringen genereres, og bruken blir ikke fakturert. Når du laster opp datavarehusets egen eksportfil, kan du opprette de manglende kundene rett fra forhåndsvisningen. Du trenger rollen FORVALTER.

Valget finnes bare for datavarehusets eksportfil. Forsystems eget CSV-format inneholder verken navn, kundenummer eller fakturareferanse, så kunder må da registreres på annen måte.

## Gjør slik

1. Last opp fila under [Bruksdata](/bruksdata), se [Last opp bruksdata fra en fil](last-opp-csv.md).
2. I forhåndsvisningen ser du en advarsel som «N organisasjoner i fila finnes ikke i kunderegisteret».
3. Trykk [Opprett N kunder fra fila](knapp:), der N er antallet.
4. Les resultatet øverst på siden, og fortsett med [Bekreft import](knapp:) hvis alt ellers er i orden.

## Hva som opprettes

For hver manglende organisasjon registreres det som datavarehuset har:

- **Virksomhetsnavn.** Mangler navnet i fila, brukes organisasjonsnummeret som navn.
- En **kundenummerregel** for alle produkter, hvis fila har kundenummer.
- **Fakturareferanse** og **bestillingsnummer**, hvis fila har dem.
- **Avtalestatus** AKTIV.

Fakturamottaker, servicekode og tilleggstekst settes ikke. Har kunden en avvikende mottaker eller deler kundenummer med andre, må du legge det inn etterpå. Se [Legg inn en kundenummerregel](legg-til-kundenummerregel.md) og [Send fakturaen til en annen enn kunden](avvikende-fakturamottaker.md). Opplysningene kommer fra datavarehuset (fra CRM). Funksjonen er en midlertidig løsning til kundene kommer fra CRM (OQ-5), så stikkprøv at de ser riktige ut.

> [!IMPORTANT]
> Kundesiden har ingen lenke i menyene i dag. Skal du rette en nyopprettet kunde, åpner forvaltningen siden med en direkte lenke de har fått.

## Slik ser du at det gikk bra

Øverst står en grønn melding som «Opprettet N kunder fra fila (navn, kundenummer og fakturareferanse)». Advarselen om manglende kunder er borte, og funnet `MANGLER_KUNDE` kommer ikke når du genererer.

## Hvis noe går galt

Kunne ikke en kunde opprettes, står den i en rød liste med organisasjonsnummer og grunn, for eksempel at en referanse er over 25 tegn. De andre kundene er opprettet likevel. Sjekk kunden som feilet, siden den kan være delvis registrert. Rett opplysningen i kilden eller på kundesiden, og last opp fila på nytt. Se [Legg inn fakturareferanse](legg-til-fakturareferanse.md) for grensen på 25 tegn.
