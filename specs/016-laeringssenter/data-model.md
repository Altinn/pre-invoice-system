# Data model: specs/016 Læringssenter

There is no database change. Everything below is read from classpath files at startup and held in memory,
immutable.

## Files

```text
src/main/resources/hjelp/
├── meny.yml                  # sections, groups, order, the "Jeg vil …" list
├── kontrollfunn.yml          # Kontrollkode name → "Slik løser du det" (Markdown)
├── kom-i-gang/*.md
├── manedsrutinen/1-sjekk-registrene.md … 6-eksporter.md
├── nar-noe-gar-galt/*.md
├── oppslag/*.md
├── slik-gjor-du/*.md         # phase 2
└── forsta-systemet/*.md      # phase 2
```

## meny.yml

```yaml
seksjoner:
  - id: kom-i-gang                 # = folder name = first URL segment
    tittel: Kom i gang
    artikler: [hva-er-forsystem, roller-og-tilgang, rundtur, prov-selv]
  - id: manedsrutinen
    tittel: Månedsrutinen
    sti: true                      # ordered path: "Steg n av N", stepper, Forrige/Neste
    artikler: [1-sjekk-registrene, 2-hent-bruksdata, 3-generer, 4-kontrollfunn, 5-godkjenn, 6-eksporter]
  - id: slik-gjor-du
    tittel: Slik gjør du
    grupper:                       # optional subheadings in the menu
      - tittel: Priser og produkter
        artikler: [lag-prisversjon, …]
jeg-vil:                           # start page list, 10–15 entries
  - tekst: endre tekstene på fakturaen
    artikkel: slik-gjor-du/endre-fakturatekster
```

Sections appear in the fixed FR-004 order; a section with no articles is not shown (research R7).

## Artikkel (front matter + body)

| Field | Required | Rule |
|---|---|---|
| `tittel` | yes | non-blank, ≤ 80 characters |
| `sammendrag` | yes | one sentence, ≤ 200 characters |
| `oppdatert` | yes | ISO date, not in the future |
| `rolle` | no | `LESER` / `FORVALTER` / `GODKJENNER` → tag "Krever rollen …" |
| `apne-i-appen` | no | absolute app path matching a GET route without ids (R4) |
| `apne-i-appen-tekst` | no | button text, default "Åpne {seksjon}" derived from the path |
| `relatert` | no | list of article ids (`seksjon/side`), each must exist |
| `nokkelord` | no | extra search words (phase 3) |

An unknown key fails loading, so a typo cannot be silently ignored.

Derived when loaded:

- `id` = `seksjon/side` (from the path) and `url` = `/hjelp/{id}`.
- `deler`: the body as an ordered list of `Html(String)` | `Blokk(String navn)`.
- `innhold`: the h2 headings as (anchor, text), for *På denne siden*.
- `steg`: for articles in a `sti` section, the position n of N plus the previous and next article.
- `tekst`: plain text, for search.

## Kontrollfunn-løsning

`Map<Kontrollkode, String html>` from `kontrollfunn.yml`. The keys must equal `Kontrollkode.values()`
exactly. The `kontrollkoder` block renders, per code, the code, a severity tag, `definisjon()`, and the
solution, with an anchor `#KODE` so the Kontroller tab can link straight to a code.

## Validation at load (one combined error message)

Each of these fails loading:

1. Front-matter rules above.
2. Every `.md` appears exactly once in `meny.yml`, and every menu entry has a file.
3. Every relative `.md` link, and every `#anker` on it, resolves.
4. External links (`http:`, `https:`, `mailto:`) are not allowed.
5. Block names are known; `knapp:` links have text.
6. `kontrollfunn.yml` covers exactly the `Kontrollkode` values.
7. `jeg-vil` entries point to existing articles.

App-route links are checked in `HjelpLenkerIT` (needs the web context).
