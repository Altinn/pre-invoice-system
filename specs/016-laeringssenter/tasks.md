# Tasks: Læringssenter — help and onboarding inside forsystem

**Input**: Design documents from `/specs/016-laeringssenter/` ([plan.md](plan.md), [spec.md](spec.md),
[research.md](research.md), [data-model.md](data-model.md), [contracts/ui.md](contracts/ui.md),
[quickstart.md](quickstart.md))

**Tests**: Included. The drift checks are functional requirements (FR-014 – FR-017), and quickstart.md names
the test classes.

**Delivery**: Three PRs (plan.md, Delivery):
- **PR 1** = Phases 1–4 (US1 + US2).
- **PR 2** = Phase 5 (US3).
- **PR 3** = Phase 6 (US4).
- Phase 7 is split across them as marked.

**Paths**: `J` = `src/main/java/no/digdir/forsystem/web/hjelp/`, `T` = `src/test/java/no/digdir/forsystem/web/hjelp/`,
`H` = `src/main/resources/hjelp/`, `V` = `src/main/resources/templates/hjelp/`.

**Writing articles**: Articles are Bokmål and use the app's exact button labels as `[Label](knapp:)`. Never
state an unconfirmed business fact; name the open question instead (OQ-2, OQ-6, OQ-23 …, constitution I).
Every article has `tittel`, `sammendrag` and `oppdatert` (data-model.md). Draft from
`docs/brukerveiledning.md` and `docs/runbook.md`, then check each claim against the current templates or
controllers before writing it. Remember: no Kunder menu, five products, and the customer detail page is not
linked from anywhere.

## Phase 1: Setup

- [ ] T001 Rebase `feat/016-laeringssenter` onto `origin/main` and check whether `feat/015-forside-dashbord`
  has merged. If it has, note it for T024; if not, leave the 015 files alone (plan.md, Sequencing).
- [ ] T002 Add `org.commonmark:commonmark`, `commonmark-ext-gfm-tables`, `commonmark-ext-yaml-front-matter`,
  `commonmark-ext-heading-anchor` and `commonmark-ext-gfm-alerts`, all `0.32.0`, to `pom.xml`, with a
  `commonmark.version` property. Run `mvn -q dependency:tree` to confirm there are no transitive additions.
- [ ] T003 [P] Create the content skeleton: `H/meny.yml` with the six sections from data-model.md (article
  lists empty except where Phase 3/4 fill them), an empty `H/kontrollfunn.yml`, and the folders `H/kom-i-gang/`,
  `H/manedsrutinen/`, `H/nar-noe-gar-galt/`, `H/oppslag/`.

## Phase 2: Foundational (blocks every story)

- [ ] T004 [P] Create the records in `J/Artikkel.java`:
  - `Artikkel` (id, seksjon, side, tittel, sammendrag, rolle, apneIAppen, apneIAppenTekst, relatert,
    oppdatert, nokkelord, deler, innhold, tekst)
  - `Del`, a sealed type with `Html(String)` and `Blokk(String navn)`
  - `Overskrift` (anker, tekst)
  - `Steg` (nr, av, forrige, neste)
- [ ] T005 [P] Create `J/Meny.java`: the records `Seksjon` (id, tittel, sti, grupper), `Gruppe` (tittel,
  artikkelIder) and `JegVil` (tekst, artikkelId), parsed from `meny.yml` with SnakeYAML (`new Yaml()`, a
  typed `Map` read with no arbitrary class construction). Add `synligeSeksjoner()`, which returns only
  sections with articles, in FR-004 order (research R7).
- [ ] T006 Create `J/HjelpRenderer.java`. One configured commonmark `Parser` and `HtmlRenderer`, with
  `escapeHtml(true)` and `sanitizeUrls(true)`. It must:
  - Add attributes: `ds-heading` + `data-size` (h2 = sm, h3 = xs), `ds-table` + `data-size="sm"` on
    tables, `ds-link` on links.
  - Render the alert node as `div.ds-alert` with `data-color` (TIP = info, IMPORTANT = warning,
    WARNING = danger) and a bold Norwegian label (*Tips* / *Viktig* / *Advarsel*). If 0.32 does not allow
    overriding the alert renderer, post-process its markup (research R5).
  - Render a link with destination `knapp:` as `<span class="knapp">text</span>`.
  - Rewrite relative `*.md` links (with an optional `#anker`) to `/hjelp/{seksjon}/{side}`, and collect
    every link target for validation.
  - Turn a paragraph that is exactly `{{navn}}` into a `Blokk` part. `render` returns `deler`, the h2
    headings, every link, and plain text.
- [ ] T007 Create `J/Hjelpebibliotek.java`, a `@Component` that at construction:
  - Reads `classpath:hjelp/**/*.md`, `meny.yml` and `kontrollfunn.yml` through
    `PathMatchingResourcePatternResolver`, and renders through `HjelpRenderer`.
  - Builds `Steg` for `sti` sections.
  - Runs every validation in data-model.md ("Validation at load", 1–7) and collects all problems into one
    `IllegalStateException` that names each file.
  - Exposes `artikkel(seksjon, side)`, `meny()`, `jegVil()`, `kontrollfunn()` (as
    `Map<Kontrollkode, String html>`), `alle()` and `appLenker()` (id → paths, for T012).

  Take the resource root as a constructor argument (default `hjelp`) so tests can load fixture folders.
- [ ] T008 [P] Write `T/HjelpRendererTest.java` (unit) covering:
  - each alert type becomes `ds-alert` with the right colour and label
  - `[Generer](knapp:)` becomes `span.knapp`
  - `<script>` and `<b>` in content are escaped
  - `../manedsrutinen/3-generer.md#hvis-noe-gar-galt` is rewritten
  - h2 gets an id and appears in `innhold`
  - `{{kontrollkoder}}` becomes a `Blokk`
  - a table gets `ds-table`
- [ ] T009 [P] Write `T/HjelpebibliotekTest.java` (unit) with fixture folders under
  `src/test/resources/hjelp-feil/<case>/`. It needs one case per validation rule, each asserting the message
  names the file and the problem:
  - missing `tittel`
  - unknown front-matter key
  - `oppdatert` in the future
  - article not in the menu
  - menu entry with no file
  - article listed twice
  - broken `.md` link
  - broken `#anker`
  - `https:` link
  - unknown block
  - empty `knapp:` text
  - `kontrollfunn.yml` missing a code
  - `kontrollfunn.yml` with an unknown key
  - `jeg-vil` pointing nowhere

  Add one case where the real `hjelp/` content loads cleanly.
- [ ] T010 Create `V/fragmenter.html`, holding these fragments:
  - `meny(gjeldende)`: section `ds-details`, groups, numbered `sti` items, `aria-current`, and the narrow
    `Innhold` variant
  - `aside(artikkel)`: *På denne siden* (only with two or more headings), *Gå til i appen*, *Relatert*;
    class `ikke-utskrift`
  - `stegviser(steg)`
  - `forrigeNeste(steg)`
  - `blokk(navn)`, which dispatches to `kontrollkoder`, `livslop-kjoring` and `livslop-prisversjon`
    (T018, T026)

  The file name is excluded from `TemplateKonvensjonTest` by convention.
- [ ] T011 Add `/* ---- Læringssenter (specs/016) ---- */` styles to
  `src/main/resources/static/css/forsystem.css`:
  - `.hjelp-oppsett` grid (16rem / minmax(0, 44rem) / 14rem; aside below the article under 64rem; menu
    becomes `details` under 48rem; no horizontal overflow)
  - `.artikkel` typography spacing
  - `.knapp`, styled like a small secondary button and not interactive
  - `.stegviser`
  - `.livslop`
  - print rules hiding the menu, aside, stepper and Forrige/Neste

  Use Designsystemet tokens only, with no inline styles.

**Checkpoint**: The library loads the (still almost empty) content, and the unit tests are green.

## Phase 3: User Story 1 — a new user learns the monthly routine inside the app (P1) 🎯 MVP

**Goal**: Hjelp in the top bar, the start page, the three-column article page, Kom i gang and Månedsrutinen.

**Independent test**: A newcomer runs one test month in tt02 using only `/hjelp` (spec US1, quickstart).

- [ ] T012 [P] [US1] Write `T/HjelpLenkerIT.java` (extends `IntegrationTest`). It collects the GET
  `PathPattern`s from `RequestMappingHandlerMapping` and asserts every path in `Hjelpebibliotek.appLenker()`
  matches one of them. Paths with a numeric segment are rejected. The failure message lists article → path.
- [ ] T013 [P] [US1] Write `T/HjelpIT.java` (extends `IntegrationTest`, MockMvc, `@WithMockUser(roles = "LESER")`):
  - `/hjelp` returns 200 with the four cards, `Jeg vil …`, and a menu with no current item
  - every article in `alle()` returns 200, with its h1, its summary, its menu item marked `aria-current="page"`,
    and «Sist oppdatert»
  - a Månedsrutinen step shows «Steg 3 av 6» and the Forrige/Neste titles
  - an article with `rolle` shows «Krever rollen …»
  - `/hjelp/finnes/ikke` returns 404 with «Fant ikke siden» and a link to `/hjelp`
  - no response contains `<script` from content
- [ ] T014 [US1] Add `/hjelp` to `src/test/java/no/digdir/forsystem/web/LayoutIT.java` and assert that the
  Hjelp link (outside `nav.hovedmeny`) has `aria-current="page"` on `/hjelp` and not on `/kjoringer`.
- [ ] T015 [US1] Create `J/HjelpController.java`:
  - `GET /hjelp` → `hjelp/start`, with model `meny`, `jegVil` and the four card targets
  - `GET /hjelp/{seksjon}/{side}` → `hjelp/artikkel`, or `ResponseStatusException(NOT_FOUND)`
  - `seksjon` for the top bar = `hjelp`
- [ ] T016 [US1] Create `V/start.html` and `V/artikkel.html` per contracts/ui.md, using `layout :: head`,
  `layout :: topbar('hjelp')`, breadcrumbs (no "←"), `main#hovedinnhold.side`, the `.hjelp-oppsett`
  columns, and the fragments from T010. Body parts render with `th:utext` for `Html` and
  `th:replace` for `Blokk`. The «Sist oppdatert» date is formatted `d. MMMM yyyy` in Norwegian.
- [ ] T017 [US1] Update `src/main/resources/templates/layout.html`: add the **Hjelp** link at the end of
  `.topbar-inner`, outside `nav.hovedmeny`, with `aria-current` when `seksjon == 'hjelp'`, and document
  `hjelp` in the comment listing section values. Add its spacing (`margin-inline-start: auto`) to
  forsystem.css.
- [ ] T018 [US1] Update `src/main/resources/templates/error.html`: a `status == 404` branch with h1 «Fant
  ikke siden», one sentence, and links to Forside and Læringssenter; keep the 403 and generic branches as
  they are. Add the `livslop-kjoring` and `livslop-prisversjon` blocks to `V/fragmenter.html` (an `ol.livslop`
  of status tags with transition labels, plus a screen-reader sentence). Statuses: GENERERT → GODKJENT →
  EKSPORTERT, plus FORKASTET; UTKAST → AKTIV → ARKIVERT.
- [ ] T019 [P] [US1] Write `H/kom-i-gang/hva-er-forsystem.md`, from guide §1. It covers the big picture as
  six numbered steps and what forsystem does not do (it sends no invoices; customers come from CRM).
  `apne-i-appen: /`.
- [ ] T020 [P] [US1] Write `H/kom-i-gang/roller-og-tilgang.md`, from guide §2. It covers LESER, FORVALTER
  and GODKJENNER (which actions need which role, checked against `SecurityConfig`), and the «Ingen tilgang»
  and «Siden er utløpt» pages.
- [ ] T021 [P] [US1] Write `H/kom-i-gang/rundtur.md`. Each current top-bar section gets two sentences, in
  workflow order: Produkter (incl. Kildenavn), Prisversjoner, Bruksdata, Kjøringer (tabs), Hendelseslogg,
  Hjelp. There is no Kunder section.
- [ ] T022 [P] [US1] Write `H/kom-i-gang/prov-selv.md`. It covers tt02 versus production, the demo data,
  *Nullstill periode* (tt02 and local only, what it deletes and keeps), and a suggested practice month.
- [ ] T023 [US1] Write the six Månedsrutinen steps in `H/manedsrutinen/`:
  - `1-sjekk-registrene.md`
  - `2-hent-bruksdata.md`
  - `3-generer.md`
  - `4-kontrollfunn.md`
  - `5-godkjenn.md`
  - `6-eksporter.md`

  Each uses the parts *Hvem · Før du starter · Gjør slik · Slik ser du at det gikk bra · Hvis noe går galt*
  (h2s, FR-010), with `rolle`, `apne-i-appen` and `relatert`. Draw on guide §5, §7, §8, §10 and §11 and
  runbook steps 1–5. Step 2 covers both the datavarehus fetch and the CSV fallback, from the 7th. Step 4
  links to `../oppslag/kontrollfunn.md`. Step 6 covers LG04-avstemming, the downloads, *Kopier* for the
  e-post to regnskap, and sending to DFØ. Put `{{livslop-kjoring}}` in step 3.
- [ ] T024 [US1] Fill `H/meny.yml` (kom-i-gang, manedsrutinen with `sti: true`, nar-noe-gar-galt, oppslag)
  and `jeg-vil` (10–15 entries linking only to articles that exist in PR 1).

  If specs/015 has merged (T001):
  - delete `src/main/resources/templates/laering.html` and its mapping in `HomeController`
  - add `GET /laering` → `redirect:/hjelp` in `HjelpController`
  - point the start-page card in `index.html` to `/hjelp`
  - add the redirect case to `T/HjelpIT.java`
- [ ] T025 [US1] Write `H/nar-noe-gar-galt/oversikt.md`, from guide §13, plus «Siden er utløpt», «Ingen
  tilgang» and «Eksport stoppet». Each symptom is an h2 with its cause and fix. This is the target of the
  *Noe gikk galt* card in PR 1 (research R7).
- [ ] T026 [US1] Write `H/oppslag/statuser.md`, from guide §12, with `{{livslop-kjoring}}`,
  `{{livslop-prisversjon}}`, and import and avtalestatus as a table.

**Checkpoint**: US1 is complete. `HjelpIT`, `HjelpLenkerIT` and `LayoutIT` are green, and the quickstart
manual steps 1–3 and 5–7 pass.

## Phase 4: User Story 2 — every control finding explained (P1)

**Goal**: A Kontrollfunn reference generated from `Kontrollkode`, with a solution per code, linked from the
Kontroller tab.

**Independent test**: Every `Kontrollkode` value appears on `/hjelp/oppslag/kontrollfunn` with its severity,
definition and solution (spec US2).

- [ ] T027 [P] [US2] Extend `T/HjelpebibliotekTest.java`: for the real content, `kontrollfunn().keySet()`
  equals `EnumSet.allOf(Kontrollkode.class)` and each text is non-blank. Extend `T/HjelpIT.java`: the
  Kontrollfunn page contains every code with `id="{KODE}"`, its severity tag, its `definisjon()` and «Slik
  løser du det».
- [ ] T028 [US2] Write `H/kontrollfunn.yml`: one Markdown "Slik løser du det" per `Kontrollkode` (all
  eleven), from the guide §11 table, checked against `Kontrollkode` and the Kontroller tab. Link to
  Månedsrutinen steps with relative `.md` links where useful. Customer-related fixes must not say "Kunder in
  the menu".
- [ ] T029 [US2] Add the `kontrollkoder` block to `V/fragmenter.html`: per code, `<code id="KODE">`, a
  `ds-tag` (danger for Blokkerende, warning for Advarsel, as in `kjoring/kontroller.html`), the definition,
  and «Slik løser du det:» with the solution HTML. `HjelpController` adds `kontrollkoder` and `kontrollfunn`
  to the model for articles that use the block.
- [ ] T030 [US2] Write `H/oppslag/kontrollfunn.md`. It covers what Blokkerende and Advarsel mean, how to
  sign off warnings (*Funn akseptert*), and `{{kontrollkoder}}`. `apne-i-appen: /kjoringer`. Add it to
  `meny.yml` as the first entry under Oppslag.
- [ ] T031 [US2] Update `src/main/resources/templates/kjoring/kontroller.html`: after both the «Hva betyr
  alvorlighet?» and «Hva betyr kodene?» `<details>`, add a `ds-link` «Les mer i Læringssenteret» to
  `/hjelp/oppslag/kontrollfunn`. Add a MockMvc assertion for the link in the existing Kontroller-tab
  integration test (find it with `grep -rl "Hva betyr kodene" src/test`), or in `T/HjelpIT.java` if none
  exists.

**Checkpoint**: PR 1 scope is complete. Go to Phase 7 tasks marked PR 1, then open PR 1.

## Phase 5: User Story 3 — all guidance in one place and up to date (P2) → PR 2

**Goal**: Every piece of `docs/brukerveiledning.md` lives in an article, checked against the app; the old
file becomes a pointer.

**Independent test**: A reviewer maps every section of the old guide to an article, or to a conscious drop
(spec US3, scenario 1).

- [ ] T032 [US3] Create the folders `H/slik-gjor-du/` and `H/forsta-systemet/`, and add both sections to
  `meny.yml` with the `grupper` Priser og produkter, Kunder, Bruksdata, Kjøringer and Spor.
- [ ] T033 [P] [US3] Write the Priser og produkter articles from guide §4–5 and the Kildenavn page:
  - `H/slik-gjor-du/lag-prisversjon.md`
  - `H/slik-gjor-du/endre-pris-eller-terskelverdi.md`
  - `H/slik-gjor-du/legg-inn-kontering.md` (OQ-2 named)
  - `H/slik-gjor-du/koble-kildenavn.md`
- [ ] T034 [P] [US3] Write the Kunder articles from guide §6, checked against `kunde/detalj.html`:
  - `H/slik-gjor-du/legg-til-fakturareferanse.md` (25-character limit, bestillingsnummer fallback)
  - `H/slik-gjor-du/legg-til-kundenummerregel.md` (shared kundenummer with tilleggstekst)
  - `H/slik-gjor-du/avvikende-fakturamottaker.md`

  Do NOT write `finn-og-apne-en-kunde.md` until the customer-page decision is made (spec Assumptions). Record
  the omission in the PR description.
- [ ] T035 [P] [US3] Write the Bruksdata articles from guide §7 and `docs/csv-format.md`:
  - `H/slik-gjor-du/last-opp-csv.md`
  - `H/slik-gjor-du/importer-periode-pa-nytt.md`
  - `H/slik-gjor-du/opprett-kunder-fra-fila.md`
- [ ] T036 [P] [US3] Write the Kjøringer articles from guide §8, §11 and specs/012–014:
  - `H/slik-gjor-du/endre-fakturatekster.md` (OQ-23 named)
  - `H/slik-gjor-du/forkast-og-generer-pa-nytt.md`
  - `H/slik-gjor-du/sjekk-ut-advarsler.md`
  - `H/slik-gjor-du/finn-en-faktura.md`
  - `H/slik-gjor-du/last-ned-fakturagrunnlag.md`
  - `H/slik-gjor-du/kopier-epost-til-regnskap.md`
- [ ] T037 [P] [US3] Write `H/slik-gjor-du/lag-korrigeringskjoring.md` and
  `H/forsta-systemet/korrigering-og-avregning.md` from specs/007 and the current UI (FR-020). State plainly
  what is not yet available in production (OQ-6).
- [ ] T038 [P] [US3] Write the Spor articles from guide §9:
  - `H/slik-gjor-du/hvem-endret-hva.md`
  - `H/slik-gjor-du/er-lg04-lastet-ned.md`
- [ ] T039 [P] [US3] Split `H/nar-noe-gar-galt/oversikt.md` into one article per symptom:
  - `godkjenn-er-gra.md`
  - `ingen-aktiv-prisversjon.md`
  - `ingen-bruksdata.md`
  - `import-blokkeres.md`
  - `fila-kjennes-ikke-igjen.md`
  - `eksport-stoppet.md`
  - `ingen-tilgang.md`
  - `siden-er-utlopt.md`
  - `eksportert-kan-ikke-forkastes.md`

  Keep `oversikt.md` as a short index linking to them, and update `meny.yml`.
- [ ] T040 [P] [US3] Write the rest of Oppslag:
  - `H/oppslag/roller.md` (page × role)
  - `H/oppslag/filer.md` (LG04, PDF zip, CSV, XLSX: who gets what)
  - `H/oppslag/csv-format.md` (from `docs/csv-format.md`)
  - `H/oppslag/ordliste.md` (from guide §14)
- [ ] T041 [P] [US3] Write the Forstå systemet articles:
  - `H/forsta-systemet/slik-grupperes-fakturaer.md`
  - `H/forsta-systemet/prisversjon-og-gyldighet.md`
  - `H/forsta-systemet/terskelverdi.md`
  - `H/forsta-systemet/sporbarhet.md`
  - `H/forsta-systemet/finmod-kort-fortalt.md` (only facts from docs/01; no invented prices)
- [ ] T042 [US3] Update `jeg-vil` in `H/meny.yml` to point at the new how-to articles, with 10–15 entries.
- [ ] T043 [US3] Replace `docs/brukerveiledning.md` with a short pointer: the guide lives in the app under
  **Hjelp** (`/hjelp`), and the sources are in `src/main/resources/hjelp/`. Point `README.md`, `docs/runbook.md`
  and `docs/funksjonsoversikt.md` links that target the old guide at the new places.
- [ ] T044 [US3] Write the coverage map in `specs/016-laeringssenter/migrering.md`: old guide section →
  article(s), or "dropped: reason". It is the evidence for US3 scenario 1 and goes in the PR 2 description.

**Checkpoint**: PR 2 scope is complete; all tests are green.

## Phase 6: User Story 4 — help where the user stands, and search (P3) → PR 3

**Goal**: *Hjelp til denne siden* on every main page; search in the learning center.

**Independent test**: From each main page, the link lands on the right article; ten common terms each find
the expected article first (SC-006).

- [ ] T045 [P] [US4] Write `T/HjelpSokTest.java` (unit): Norwegian normalisation (æøå kept), every word must
  match, ranking title > summary > body, and a max of 20 hits. Assert the ten SC-006 terms against the real
  library: terskelverdi, kontering, LG04, forkast, fakturareferanse, kildenavn, prisversjon, advarsel, CSV,
  korrigering.
- [ ] T046 [P] [US4] Write `T/HjelpLenkerIT.java` (or a new unit test `T/HjelpFragmentTest.java`): scan
  `src/main/resources/templates/**/*.html` for `layout :: hjelp('…')` and assert each id exists in the
  library.
- [ ] T047 [US4] Create `J/Hjelpesok.java` (research R9), using `Artikkel.tekst` and `nokkelord`.
- [ ] T048 [US4] Add `GET /hjelp/sok?q=` to `J/HjelpController.java` (an empty `q` redirects to `/hjelp`) and
  create `V/sok.html` per contracts/ui.md. Add the search form (`role="search"`) to the top of the
  `meny` fragment in `V/fragmenter.html`. Add the search cases to `T/HjelpIT.java`.
- [ ] T049 [US4] Add a fragment `hjelp(artikkelId)` to `src/main/resources/templates/layout.html`: a
  `ds-link` «Hjelp til denne siden», class `ikke-utskrift`.
- [ ] T050 [US4] Place `layout :: hjelp('…')` after the h1 of each main page:
  - `produkt/liste.html` and `produkt/kildenavn.html`
  - `prisversjon/liste.html` and `prisversjon/detalj.html`
  - `bruksdata/liste.html` and `bruksdata/forhandsvis.html`
  - `kjoring/liste.html`, and the run tabs (`kjoring/detalj.html`, `kontroller.html`, `fakturaer.html`,
    `filer.html`, `historikk.html`)
  - `kunde/detalj.html`
  - `hendelseslogg/liste.html`

  Choose the most specific article for each. On the Kontroller tab, link each code row to
  `/hjelp/oppslag/kontrollfunn#{KODE}`.

**Checkpoint**: PR 3 scope is complete.

## Phase 7: Polish and cross-cutting

- [ ] T051 [P] (PR 1) Amend `CLAUDE.md` ("Every phase ends with …") and
  `.specify/memory/constitution.md` Development Workflow (FR-018, research R11): user-visible changes update
  the matching article under `src/main/resources/hjelp/` in the same PR. Bump the constitution to 1.0.1 with
  a Sync Impact Report.
- [ ] T052 [P] (PR 1) Add an `F-HJELP-*` section to `docs/funksjonsoversikt.md` (learning center,
  Kontrollfunn reference, drift checks). Add the next free `B-nn` entry in `docs/09-beslutningslogg.md`
  (Markdown in the repo at `/hjelp`, commonmark, no CMS or screenshots), checking first that specs/015 has
  not taken the number.
- [ ] T053 (each PR) Run `mvn verify` with Docker available and get it green. Then walk through the
  quickstart.md manual checks locally with `docker compose up` in the browser, at desktop width, phone width,
  dark mode and print preview.
- [ ] T054 (PR 1, once) Do the three drift proofs in quickstart.md by hand (add a Kontrollkode, point
  `apne-i-appen` at `/kunder`, rename a step file). Confirm each fails with a clear message, then revert.
- [ ] T055 (each PR) Commit conventionally with `Signed-off-by: Adi Dahl <adi.dahl@icloud.com>`, push the
  branch, and open the PR against `main`. Ask Økonomi to review the content in PR 1 (SC-007) and PR 2.

## Dependencies

- Setup (T001–T003) → Foundational (T004–T011) → stories.
- Within Foundational: T004 and T005 come before T006 and T007, and T006 before T007. T008 and T009 can be
  written alongside T006 and T007. T010 and T011 are independent of the Java tasks.
- **US1** (T012–T026) needs Foundational only. T015 comes before T016. T017 and T018 are independent. The
  content tasks T019–T023, T025 and T026 are independent of the code and of each other, apart from T024
  (menu), which comes last.
- **US2** (T027–T031) needs Foundational, plus T015/T016 for rendering. It is independent of US1's content.
  US1 and US2 ship together as PR 1.
- **US3** (T032–T044) needs PR 1 merged. Its content tasks T033–T041 are all [P]. T042–T044 come last.
- **US4** (T045–T050) needs PR 1. It works best after PR 2, so the help links point to specific how-to
  articles, but it can link to Månedsrutinen steps without PR 2.
- Polish: T051 and T052 go with PR 1; T053–T055 apply to every PR.

## Parallel examples

- **Foundational**: T004 + T005 + T010 + T011 together; then T006; then T007 with T008 + T009.
- **US1**: T012 + T013 (tests) alongside T019 + T020 + T021 + T022 (content); T015 → T016; T017 and T018 at
  any time.
- **US2**: T027 alongside T028 (solution texts, the biggest writing task) and T029.
- **US3**: T033 – T041 are nine independent writing tasks, and can be split between people or agents by
  file group.

## Implementation strategy

1. **MVP = PR 1 (US1 + US2)**: the engine, the guardrails, the monthly path and the control-code reference.
   This alone meets the product owner's core ask, and is what Økonomi reviews first.
2. **PR 2 (US3)**: mostly writing. Retires the second copy of the guide.
3. **PR 3 (US4)**: help in context and search, once the article set is stable.

Stop after each checkpoint to validate on its own; a PR is never held back for the next story.
