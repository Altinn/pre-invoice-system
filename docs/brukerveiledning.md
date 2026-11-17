# Brukerveiledning — forsystem

Brukerveiledningen bor nå i selve appen, under **Hjelp** øverst til høyre (`/hjelp`, Læringssenter).
Der finner du hva forsystem gjør, månedsrutinen steg for steg, «Slik gjør du»-veiledninger, hjelp når noe
går galt, og oppslag (kontrollfunn, statuser, filer, CSV-format, ordliste).

Kildene er Markdown-filer i repoet, så de kan leses og rettes her uten å kjøre appen:
[`src/main/resources/hjelp/`](../src/main/resources/hjelp/). Menyen står i
[`meny.yml`](../src/main/resources/hjelp/meny.yml).

Endrer du noe brukerne ser (en side, en knapp, en status, en kontrollkode, en rolle), skal den tilhørende
artikkelen endres i samme pull request. Bygget stopper ved ødelagte lenker, en artikkel som ikke står i
menyen, og en kontrollkode uten «Slik løser du det»-tekst.

Hvor hver del av den gamle veiledningen havnet, står i
[specs/016-laeringssenter/migrering.md](../specs/016-laeringssenter/migrering.md).
