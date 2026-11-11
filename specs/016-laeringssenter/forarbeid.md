# Plan — Læringssenter (learning center) in forsystem

**Status:** Groundwork for [spec.md](spec.md), 2026-11-11. The open choices in section 6 were settled with
the recommended defaults; spec.md is the authoritative version.

## 1. Why

The app has grown through fourteen specs. A new person now meets five sections, tabs on the run page,
control codes, two kinds of findings, a sign-off step for warnings, thresholds, correction runs and four
export files. Today the help for this is:

- `docs/brukerveiledning.md`: a good Norwegian guide of about 580 lines, but it lives on GitHub, not in the
  app, and it is one long page that is hard to search.
- Small `<details>` explainers inside pages ("Hva betyr kodene?" on the Kontroller tab).
- `docs/runbook.md`: the monthly procedure, in English, written for developers and ops.

The guide is already drifting from the app. After today's commit 70273ab it still describes the Kunder menu
and customer list (§3, §6), and it says there are six products while the start page says five. A learning
center that lives in the app and is checked by tests fixes both problems.

**Goal:** a new Økonomi or forvaltning user can open forsystem and, without help from a colleague, learn
what the system does, run a month from start to finish, and find what to do when something goes wrong.

**Non-goals:** a CMS, editing in the browser, video, per-user progress tracking, and developer or ops
documentation (that stays in `docs/` and the runbook).

## 2. What it looks like

A **Hjelp** link at the right end of the top bar on every page opens `/laering`. Inside, the page has three
columns:

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ forsystem Digdir   Produkter  Prisversjoner  Bruksdata  Kjøringer  Hendelseslogg  Hjelp │
├───────────────────┬──────────────────────────────────────────┬───────────────────────┤
│ Søk i hjelpen  [ ]│ Læringssenter › Månedsrutinen            │ PÅ DENNE SIDEN        │
│                   │                                          │ Før du starter        │
│ Kom i gang      ▾ │ Steg 3 av 6: Generer kjøringen           │ Gjør slik             │
│ Månedsrutinen   ▾ │ ●──●──◉──○──○──○                          │ Slik ser du at det    │
│   1 Sjekk regist. │                                          │   gikk bra            │
│   2 Hent bruk     │ Hvem: FORVALTER   Tid: ca. 5 min          │ Hvis noe går galt     │
│ ▸ 3 Generer       │                                          │                       │
│   4 Kontrollfunn  │ Før du starter                           │ ───────────────       │
│   5 Godkjenn      │  ✓ Bruksdata for perioden er importert   │ GÅ TIL I APPEN        │
│   6 Eksporter     │                                          │ [Åpne Kjøringer →]    │
│ Slik gjør du    ▸ │ Gjør slik                                 │                       │
│ Når noe går galt▸ │  1. Gå til Kjøringer.                     │ RELATERT              │
│ Oppslag         ▸ │  2. Velg periode og trykk [Generer].      │ Kontrollfunn          │
│ Forstå systemet ▸ │  …                                        │ Forkast og generer    │
│                   │                                          │   på nytt             │
│                   │ ‹ Forrige: Hent bruksdata  Neste: Gå gjennom kontrollfunn › │
└───────────────────┴──────────────────────────────────────────┴───────────────────────┘
```

- **Left:** sections with collapsible groups. The current article is marked with `aria-current="page"`, the
  same convention as the main menu.
- **Middle:** the article, with breadcrumbs (the template test forbids "← X" links), and **Forrige / Neste**
  at the bottom inside ordered paths such as Månedsrutinen.
- **Right:** "På denne siden" (a table of contents built from the h2 headings), **Gå til i appen** deep
  links, and **Relatert** articles.
- **Narrow screens:** the sidebar folds into a `<details>` "Innhold" above the article, and the right column
  moves below it.
- **Styling:** Designsystemet only (`ds-card`, `ds-alert`, `ds-tag`, `ds-link`). Button names in the text
  get a small button-like style, so **[Generer]** looks like the control the reader will see.

The `/laering` front page shows four large cards and the "Jeg vil …" list:

| Kort | For |
|---|---|
| **Ny her? Start her** | The first day: what forsystem is, roles, a tour of the screens |
| **Månedsrutinen steg for steg** | The six steps from usage data to LG04 |
| **Noe gikk galt** | Searching by symptom |
| **Oppslag** | Control codes, statuses, files, glossary |

Below the cards comes **Jeg vil …**, a list of the 10–15 most common tasks, each linking straight to its
how-to article. This is the "if you want to do this, do that" entry point.

## 3. Information architecture

The structure is loosely based on Diátaxis (tutorial, how-to, reference, explanation) with Norwegian,
task-first section names. About 45 articles in total, most of them short.

### Kom i gang (tutorial, read once)
1. Hva er forsystem? The big picture: from usage data to invoice in Unit4, and what forsystem does *not*
   do (it does not send invoices, and customers are owned by CRM).
2. Roller og tilgang: LESER, FORVALTER, GODKJENNER, plus the "Ingen tilgang" and "Siden er utløpt" pages.
3. En rundtur i skjermbildene: each menu section in two sentences, in workflow order.
4. Prøv selv i testmiljøet: tt02, the demo data, and *Nullstill periode* so a month can be practised again.

### Månedsrutinen (the core tutorial, an ordered path with a stepper)
Each step uses the same template: **Hvem · Før du starter · Gjør slik · Slik ser du at det gikk bra · Hvis
noe går galt · Neste steg**.

1. Sjekk registrene: active price version, kontering, kildenavn.
2. Hent bruksdata: from the data warehouse, or by CSV upload; preview; confirm.
3. Generer kjøringen: period, summary, texts on the invoice.
4. Gå gjennom kontrollfunn: blocking findings versus warnings, *Funn akseptert*, reconciling usage data.
5. Godkjenn.
6. Eksporter og send videre: LG04 reconciliation, downloads, *Kopier e-post* to regnskap, sending to DFØ.

### Slik gjør du (how-to, one task per article, titled "Slik …")
- **Priser og produkter:** Lag årets prisversjon · Endre en pris eller terskelverdi · Legg inn kontering ·
  Koble et kildenavn fra datavarehuset
- **Kunder:** Finn og åpne en kunde · Legg til fakturareferanse (standard / per produkt) · Legg til
  kundenummerregel og del et kundenummer · Avvikende fakturamottaker
- **Bruksdata:** Last opp CSV når datavarehuset ikke svarer · Importer en periode på nytt · Opprett
  manglende kunder fra fila
- **Kjøringer:** Endre tekstene på fakturaen · Forkast og generer på nytt · Sjekk ut advarsler · Finn en
  bestemt faktura · Last ned fakturagrunnlag til Excel · Lag en korrigeringskjøring (specs/007; it is barely
  documented today)
- **Spor:** Finn ut hvem som endret noe · Sjekk om LG04 allerede er lastet ned

### Når noe går galt (troubleshooting, titled by symptom)
Godkjenn er grå · «Ingen aktiv prisversjon dekker perioden» · «Ingen bruksdata for perioden» · Import
blokkeres · Fila kjennes ikke igjen · Eksport stoppet · Ingen tilgang · Siden er utløpt · En eksportert
kjøring kan ikke forkastes

### Oppslag (reference, generated from code where possible)
- **Kontrollfunn: alle koder.** Generated from the `Kontrollkode` enum (severity and definition), plus a
  hand-written "Slik løser du det" per code. A test fails if a code has no "løser du det" text.
- **Statuser og livssykluser:** price version, import, run, agreement status.
- **Roller og tilganger:** what each role can do, page by page.
- **Filene:** LG04, PDF zip, CSV, XLSX, and who receives each.
- **CSV-formatet for bruksdata.**
- **Ordliste.**

### Forstå systemet (explanation, for people who want the why)
How invoices are grouped (customer × kundenummer, references, `DELT_REFERANSE`) · Price versions and the
validity rule (first day of the period) · The threshold and why it exists · Traceability from usage-data
row to invoice line · Correction runs and avregning · FinMod in brief

## 4. How it is built

### Recommended: Markdown files in the repo, rendered on the server

```
src/main/resources/laering/
  meny.yml                      # sections, order, ordered paths (Forrige/Neste)
  kom-i-gang/hva-er-forsystem.md
  manedsrutinen/3-generer.md
  slik-gjor-du/endre-fakturatekster.md
  …
```

Each file starts with front matter:

```yaml
---
tittel: Generer kjøringen
sammendrag: Lag fakturagrunnlaget for en periode og se hva kontrollene fant.
rolle: FORVALTER              # shown as a tag; optional
apne-i-appen: /kjoringer      # the "Gå til i appen" button; must be a real route
relatert: [manedsrutinen/4-kontrollfunn, slik-gjor-du/forkast-og-generer]
oppdatert: 2026-11-11
---
```

- **Rendering:** commonmark-java (`org.commonmark:commonmark` plus the gfm-tables, heading-anchor and
  yaml-front-matter extensions). It is small, BSD-licensed and has no transitive dependencies. YAML is read
  with SnakeYAML, which is already on the classpath through Spring Boot. The only new dependency is
  commonmark.
- **`Laeringsbibliotek`** (a new `web.laering` package) loads every file at startup, renders it once, builds
  the table of contents from the h2/h3 headings, and holds the menu tree. **`LaeringController`** serves
  `GET /laering` and `GET /laering/{seksjon}/{side}`. All of this is read-only, so `SecurityConfig` needs no
  change: every signed-in user, LESER and up, can read it.
- **Callouts:** GitHub-style `> [!TIPS]`, `> [!VIKTIG]` and `> [!ADVARSEL]` are rendered as `ds-alert`.
  Writers never write raw HTML; raw HTML in Markdown is escaped.
- **Diagrams:** the guide's Mermaid diagrams would need a roughly 3 MB JavaScript library, and the app does
  not load assets from the internet (Constitution V). Instead, the run and price-version lifecycles become
  small static SVGs or HTML/CSS state rows, and the monthly flow becomes the stepper.
- **Live blocks:** a few pages pull from code so they cannot drift: the control-code table from
  `Kontrollkode`, and the dimension names from `Dimensjonsnavn`. This uses a placeholder such as
  `{{kontrollkoder}}` in the Markdown that the library replaces with rendered HTML.
- **The files read well on GitHub too.** That lets `docs/brukerveiledning.md` shrink to a pointer, so there
  is one source and not two copies that drift apart.

**Why not Thymeleaf templates per article?** Writing prose in HTML is slow, and it puts off the people who
know the domain (Økonomi, forvaltning) from proposing edits in a PR. Markdown keeps the content editable by
non-developers through GitHub's web editor while staying "via code" with no CMS, which is what was asked
for.

### Tests that stop it from drifting (part of `mvn verify`)
- Every article parses, has a `tittel` and `sammendrag`, and appears exactly once in `meny.yml`.
- Every internal link (`/laering/...`) points to an article that exists.
- Every app link (`apne-i-appen`, and links such as `/kjoringer` in the text) matches a real GET mapping. An
  integration test checks them against `RequestMappingHandlerMapping`, so removing a route breaks the build,
  as removing the customer list would have.
- Every `Kontrollkode` has a "Slik løser du det" entry.
- The rendered pages pass the existing `TemplateKonvensjonTest` rules: no inline style, no external URL, the
  shared head and top bar.
- `WebRoleIT`: LESER can open `/laering`.

### Process
Add one line to the definition of done in the speckit template or checklist: *"Endrer funksjonen noe
brukeren ser, er artikkelen i læringssenteret oppdatert."* Each spec's quickstart already says what changed
for the user; the matching article change goes in the same PR.

## 5. Phases

Each phase is its own PR, shippable on its own.

| Fase | Innhold | Ferdig når |
|---|---|---|
| **1. Skjelett + kjernen** | Engine, three-column layout, `/laering` front page, Hjelp link in the top bar, *Kom i gang* (4), *Månedsrutinen* (6 + stepper), the generated *Kontrollfunn* reference, all tests above | A new user can follow the monthly routine using only the app; `mvn verify` green |
| **2. Flytt resten** | *Slik gjør du*, *Når noe går galt*, the rest of *Oppslag* and *Forstå systemet*, moved from brukerveiledning and the runbook and updated against the app as it is now (Kunder, five products, correction runs); `brukerveiledning.md` becomes a pointer | Nothing in brukerveiledning is lost; no stale description |
| **3. Hjelp der du står** | A "Hjelp til denne siden" link on each app page (a `layout :: hjelp('…')` fragment pointing to the right article); the "Hva betyr …?" explainers link on to "Les mer"; a simple server-side search over titles, summaries and text | Every main page links to its article |
| **Senere (valgfritt)** | Automatic screenshots from the demo data, a "Var dette nyttig?" button, a role filter ("vis det jeg har tilgang til") | — |

Phase 1 is roughly two to three days of work including content. Phase 2 is mostly writing, with structure
and first drafts from me and review from someone in Økonomi.

## 6. Things to settle first

1. **Name and URL:** *Læringssenter* (`/laering`) or *Hjelp* (`/hjelp`)? Recommendation: the link in the
   menu says **Hjelp** (short, everyone understands it), the page heading says **Læringssenter**, and the
   URL is `/hjelp`.
2. **Audience:** only users (Økonomi, forvaltere, godkjennere), or ops and developers too? Recommendation:
   users only; the runbook stays for ops.
3. **Markdown + commonmark** (recommended) or Thymeleaf templates per article?
4. **Single source:** should `docs/brukerveiledning.md` be retired in favour of the learning center?
   Recommendation: yes, in phase 2.
5. **Screenshots in v1?** Recommendation: no. They go stale with every UI change; deep links plus
   button-styled names are enough to start with.
6. **Coordination with the front-page plan running in another session:** the front page should probably get
   a "Ny her?" card that points to `/laering`. The Hjelp link in the top bar touches `layout.html`, so the
   two changes need to be sequenced.

## 7. Found along the way

- `docs/brukerveiledning.md` §3 and §6, and the runbook step 1, still describe the Kunder menu and customer
  list removed in 70273ab, and they say six products where the start page says five.
- After 70273ab, nothing in the templates links to `/kunder/{id}`, so the customer detail page has no way
  in. The *Finn og åpne en kunde* article cannot be written until it is decided how users reach a customer:
  from an invoice row, from a findings row, or by searching for an organisation number.
- Correction runs (specs/007) get one sentence in the user guide today.
