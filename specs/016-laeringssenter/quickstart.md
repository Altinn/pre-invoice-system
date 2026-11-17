# Quickstart: validating specs/016

## Automated

```bash
mvn verify   # Testcontainers needs a running Docker (DOCKER_HOST set when using Colima)
```

Expect these to be green:

- `HjelpebibliotekTest` (unit): loading the real content, plus fixtures for every validation rule in
  data-model.md (a missing title, an article not in the menu, a broken `.md` link, an external link, an
  unknown block, a missing `kontrollfunn.yml` entry). Each fails with a message naming the file.
- `HjelpRendererTest`: alerts → `ds-alert` with Norwegian labels, `knapp:` → `span.knapp`, raw HTML
  escaped, `.md` links rewritten, h2 anchors and *På denne siden*.
- `HjelpIT`: `/hjelp` and every article answer 200 for LESER; an unknown article answers 404;
  `/laering` → 302 `/hjelp`.
- `HjelpLenkerIT`: every app link matches a GET route (research R4).
- `LayoutIT`: the new row `/hjelp` marks Hjelp as current; the main menu has no current item there.
- `TemplateKonvensjonTest`: covers the new templates automatically.
- Phase 3: `HjelpSokTest`, and the template scan for `layout :: hjelp('…')`.

## Proof that drift is caught (do once, by hand)

1. Add a value to `Kontrollkode` and run `mvn verify`. It fails, naming the code missing from
   `kontrollfunn.yml`.
2. Change an article's `apne-i-appen` to `/kunder`. `HjelpLenkerIT` fails, naming the article and the path.
3. Rename `manedsrutinen/3-generer.md`. Loading fails: the menu entry and links to it are reported.

Revert each change afterwards.

## Manual walk-through (local, `docker compose up`, then http://localhost:8080)

1. Any page: **Hjelp** is at the right of the top bar; pressing it opens Læringssenter.
2. *Ny her? Start her* opens Kom i gang; the menu marks the article.
3. *Månedsrutinen*, Steg 3: «Steg 3 av 6», the stepper, *Gå til i appen* → Kjøringer, Forrige/Neste.
4. Kjøringer › a run › Kontroller › «Hva betyr kodene?» › *Les mer i Læringssenteret* → the Kontrollfunn
   reference, with every code and its *Slik løser du det*.
5. Narrow the window to phone width: the menu folds into *Innhold*, nothing scrolls sideways.
6. Print preview of an article: only the article.
7. `/hjelp/finnes/ikke` → «Fant ikke siden» with a link to Læringssenter.

## Acceptance with users (SC-001, SC-007)

- An Økonomi reviewer reads Månedsrutinen and Kontrollfunn against tt02 before phase 1 is released.
- A newcomer runs one test month in tt02 using only the learning center (target: under 45 minutes, with no
  questions asked).
