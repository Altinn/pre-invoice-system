# Research: specs/016 Læringssenter

## R1. Content format and renderer

- **Decision**: Markdown files with YAML front matter, rendered by commonmark-java **0.32.0**
  (`org.commonmark:commonmark` with `commonmark-ext-gfm-tables`, `commonmark-ext-yaml-front-matter`,
  `commonmark-ext-heading-anchor` and `commonmark-ext-gfm-alerts`, all 0.32.0 on Maven Central). Menu and
  control-code solutions are YAML, read with SnakeYAML 2.4, which Spring Boot already brings in.
- **Rationale**: The product owner wants content "in code, no CMS" that Økonomi can propose edits to in
  GitHub's web editor. Markdown renders readably on GitHub as well, so reviewing a content PR needs no app.
  commonmark-java is the CommonMark reference implementation for Java, has no transitive dependencies, is
  BSD-2 licensed, and has an AST and attribute/node-renderer hooks for the Designsystemet classes.
  Renovate already watches the pom.
- **Alternatives**: Thymeleaf template per article (prose in HTML is slow to write and puts off
  non-developers). flexmark-java (heavier, more than needed). Asciidoctor (JRuby, far too heavy). A
  headless CMS (explicitly ruled out).

## R2. When content is parsed

- **Decision**: Once, at startup, into an immutable in-memory `Hjelpebibliotek`. Problems found while
  loading (unknown front-matter keys, missing title or summary, an article not in the menu or listed twice, a
  broken link between articles, an unknown block name) fail startup with one message listing every
  problem.
- **Rationale**: Content ships inside the jar, so it cannot change at runtime. About 45 small files take
  milliseconds. Failing fast means `mvn verify` (every `@SpringBootTest`) catches content errors before
  merge; production can never start with broken help.
- **Alternatives**: Parsing per request (wasted work, and errors only show at read time). A build-time
  Maven plugin (more moving parts).

## R3. Links that work both on GitHub and in the app

- **Decision**: Authors link between articles with relative Markdown paths
  (`[Generer kjøringen](../manedsrutinen/3-generer.md)`, optionally with `#anker`). The renderer rewrites
  them to `/hjelp/manedsrutinen/3-generer`. Links into the app are written as absolute app paths
  (`/kjoringer`). Anything else (`http…`) is rejected, because the app loads nothing from the internet and
  help should not send users elsewhere.
- **Rationale**: The same file is clickable on GitHub and in the app, and both kinds of links can be checked
  mechanically.

## R4. Checking app links against real routes (FR-016)

- **Decision**: `HjelpLenkerIT` takes the `RequestMappingHandlerMapping` bean, collects the `PathPattern`s
  of every GET mapping, and asserts that each app link in the library (`apne-i-appen` and links in the text)
  matches at least one of them. Links containing an id (`/kjoringer/42`) are rejected: articles link to
  pages, not to data.
- **Rationale**: This is the class of drift that removing the customer list (70273ab) would have caused. An
  integration test sees the real mappings; a hand-kept list would drift itself.

## R5. Callouts, button names, live blocks

- **Callouts**: GitHub alert syntax `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, parsed by
  `commonmark-ext-gfm-alerts` and rendered by our own `NodeRenderer` for the alert node as
  `<div class="ds-alert" data-color="info|warning|danger">` with the Norwegian label *Tips* / *Viktig* /
  *Advarsel*. They render as alerts on GitHub too. To verify during implementation: in 0.32, overriding the
  alert node's renderer through `HtmlRenderer.builder().nodeRendererFactory(...)`. Fallback: post-process
  the extension's default `markdown-alert` markup.
- **Button names**: `[Generer](knapp:)`. A link with the destination `knapp:` renders as
  `<span class="knapp">Generer</span>`. On GitHub it shows as plain link text, which reads fine. Test: a
  `knapp:` link with empty text fails loading.
- **Live blocks**: a paragraph that is exactly `{{navn}}` becomes a block placeholder. The article's body is
  stored as a list of parts (HTML or block name), and the template renders blocks with Thymeleaf fragments
  from `hjelp/blokker.html`. Blocks in phase 1: `kontrollkoder` (from `Kontrollkode` plus the solution
  texts), `livslop-kjoring` and `livslop-prisversjon` (HTML state rows, FR-011). An unknown name fails
  loading.
- **Alternatives**: Raw HTML in Markdown (FR-009 forbids it). Mermaid (about 3 MB of JavaScript; the app
  loads nothing from outside, and a vendored copy is heavy for four diagrams).

## R6. Control-code solutions (FR-012, FR-017)

- **Decision**: `src/main/resources/hjelp/kontrollfunn.yml` maps every `Kontrollkode` name to a short
  Markdown "Slik løser du det" text. Loading fails if a code is missing or a key is not a code. Severity and
  definition always come from the enum (the same source the Kontroller tab uses).
- **Rationale**: The solutions are content, so they belong with the content and can be edited without
  touching Java. Adding a code without its text breaks every integration test at once, with the code named in
  the message.
- **Alternative**: A third constructor argument on the enum, enforced by the compiler. That is stronger, but
  it puts help prose in domain code and makes Økonomi's edits Java edits.

## R7. Phase-1 menu with empty sections

- **Decision**: The menu shows a section only when it has at least one article. The order stays fixed
  (FR-004). Phase 1 ships Kom i gang, Månedsrutinen, Når noe går galt (one overview article from the
  guide's §13 table, split per symptom in phase 2) and Oppslag (Kontrollfunn, Statuser). Slik gjør du and
  Forstå systemet appear in phase 2. The start page's *Jeg vil …* list links only to articles that exist,
  checked at load.
- **Rationale**: No "kommer snart" pages; every link leads somewhere real.

## R8. URL and the specs/015 placeholder

- **Decision**: `/hjelp` per the spec. specs/015 (not yet merged) adds a placeholder `GET /laering`
  (`laering.html`) and a start-page card linking to it. Whichever of the two merges second adapts: 016
  deletes `laering.html` and the placeholder mapping, points the card to `/hjelp`, and keeps
  `GET /laering` → `302 /hjelp` so old links still work.
- **Rationale**: One learning center, no dead placeholder; the card the product owner already saw keeps
  working.

## R9. Search (phase 3)

- **Decision**: In-memory search over the library. Text is normalised (lowercase, NFKD without combining
  marks, but æ/ø/å kept as letters), every query word must match, and hits rank title > summary > body. It
  shows up to 20 hits with their summaries. No index library.
- **Rationale**: About 45 articles; a linear scan is microseconds. Lucene would be overkill.

## R10. Help in context (phase 3)

- **Decision**: A fragment `layout :: hjelp('manedsrutinen/3-generer')` renders *Hjelp til denne siden* on a
  page. A unit test scans the templates for `layout :: hjelp('…')` and checks each id against the
  library, so a renamed article cannot leave a dead help link.

## R11. Definition of done (FR-018)

- **Decision**: Amend `CLAUDE.md` ("Every phase ends with …") and the constitution's Development Workflow
  ("docs updated when behaviour changes") to name the learning center explicitly: "user-visible changes
  update the matching article under `src/main/resources/hjelp/` in the same PR". Constitution PATCH bump to
  1.0.1, with a Sync Impact Report.
