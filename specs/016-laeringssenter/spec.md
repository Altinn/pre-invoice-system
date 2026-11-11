# Feature Specification: Læringssenter — help and onboarding inside forsystem

**Feature Branch**: `feat/016-laeringssenter`

**Created**: 2026-11-11

**Status**: Draft — ready for planning

**Input**: The product owner: new people are joining and find the app hard to navigate as it grows. They want a
learning center reached from a link, with a sidebar of sections and the content in the middle and right,
structured as "if you want to do this, do that", written in Norwegian, maintained in code (no CMS). The
groundwork, with the reasons behind each decision, is in [forarbeid.md](forarbeid.md).

## Context

forsystem has grown through fourteen specs: five menu sections, tabs on the run page, eleven control codes
of two severities, sign-off of warnings, thresholds, correction runs and four export files. Help today is
spread across three places:

- `docs/brukerveiledning.md`: a Norwegian user guide of about 580 lines. It is good, but it lives on GitHub,
  not in the app, is one long page, and is already out of date (it still describes the Kunder menu and list
  removed on 2026-11-11, and six products where the start page says five).
- Small "Hva betyr …?" explainers inside a few pages, such as the control codes on the Kontroller tab.
- `docs/runbook.md`: the monthly procedure in English, written for operations.

Nothing in the app tells a new user where to begin, and nothing stops the guide from drifting away from the
app.

## Clarifications

### Session 2026-11-11 (decisions settled with the product owner)

- Q: What is it called, and where does it live? → A: The top bar gets a link **Hjelp** at its right end, on
  every page. It opens the start page of the learning center, headed **Læringssenter**, at `/hjelp`.
- Q: Who is it for? → A: The people who use the app: Økonomi, forvaltere and godkjennere. Operations and
  developer documentation stays in `docs/` and the runbook.
- Q: How is content maintained? → A: As text files in the repository, reviewed through pull requests like
  code. No CMS and no editing in the browser. Someone without developer tools can propose a change through
  GitHub's web editor.
- Q: What happens to `docs/brukerveiledning.md`? → A: Its content moves into the learning center and is
  updated against the app as it is now. Afterwards the file only points to the learning center, so there is
  one source.
- Q: Screenshots? → A: Not in this feature. They go stale with every UI change. Articles name buttons
  exactly as the app shows them, styled so they are recognisable, and link straight to the right page.
- Q: Language? → A: All content and all learning-center UI text in Norwegian Bokmål, using the app's own
  terms (kjøring, kontrollfunn, prisversjon …).
- Q: Who can read it? → A: Every signed-in user, LESER and up. Nothing in it is secret, but it stays behind
  sign-in like the rest of the app.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — A new user learns the monthly routine inside the app (Priority: P1)

A new forvalter at Økonomi signs in for the first time. They press **Hjelp**, choose **Ny her? Start her**,
and read what forsystem does, which role they have, and what each menu section is for. Then they open
**Månedsrutinen** and follow it step by step for a test month in tt02: check the registers, fetch usage data,
generate the run, go through the findings, approve, export and send on. Each step tells them who does it,
what must be in place first, exactly what to press, how to see that it worked, and what to do if it did not.
A button on each step takes them to the right page in the app.

**Why this priority**: This is the problem the product owner described. Without it nothing else in the
learning center matters, and on its own it already lets a newcomer run a month without a colleague beside
them.

**Independent Test**: Give a person who has never used forsystem a tt02 account and a test file. Using only
the learning center, they generate, approve and export a run for one month.

**Acceptance Scenarios**:

1. **Given** any page in the app, **When** the user presses **Hjelp** in the top bar, **Then** the
   Læringssenter start page opens, showing the entry points *Ny her? Start her*, *Månedsrutinen steg for
   steg*, *Noe gikk galt* and *Oppslag*, plus a *Jeg vil …* list of common tasks.
2. **Given** the learning center is open, **When** the user looks at the left column, **Then** they see the
   sections Kom i gang, Månedsrutinen, Slik gjør du, Når noe går galt, Oppslag and Forstå systemet, with the
   current article marked.
3. **Given** a step of Månedsrutinen is open, **When** the user reads it, **Then** it shows which step it is
   (for example "Steg 3 av 6") and a progress indicator, links to the previous and next step, and has the
   parts *Hvem*, *Før du starter*, *Gjør slik*, *Slik ser du at det gikk bra* and *Hvis noe går galt*.
4. **Given** an article is open, **When** the user looks at the right column, **Then** they see *På denne
   siden* (the article's headings, linked), *Gå til i appen* (a link to the page the article is about, when
   there is one) and *Relatert* (other articles).
5. **Given** an article names a button, **When** it is shown, **Then** the button name is exactly the label
   the app uses and is visually marked as a button.
6. **Given** the user signed in with role LESER, **When** they open any article, **Then** it is shown, and
   articles about actions the role cannot perform say which role is needed.
7. **Given** a narrow screen such as a phone, **When** the user opens an article, **Then** the section
   menu folds into an expandable *Innhold* above the article and nothing scrolls sideways.

---

### User Story 2 — The user finds what a control finding means and how to fix it (Priority: P1)

A godkjenner sees `MANGLER_KUNDENUMMER` on the Kontroller tab and does not know what to do. In the learning
center, under **Oppslag → Kontrollfunn**, every code is listed with its severity, what it means and how to
resolve it, and the list always matches the codes the app can raise.

**Why this priority**: Findings are where users get stuck, and the one place where wrong help costs money: an
approval delayed, or a finding misunderstood. It belongs in the first delivery together with Story 1.

**Independent Test**: For every control code the app can raise, open the Kontrollfunn reference and confirm
the code is there with its severity, meaning and a "Slik løser du det" text.

**Acceptance Scenarios**:

1. **Given** the Kontrollfunn reference is open, **When** the user reads it, **Then** every control code the
   app can raise is listed with its severity (Blokkerende or Advarsel), the same definition the Kontroller
   tab shows, and a *Slik løser du det* text.
2. **Given** a developer adds a new control code, **When** they build the app without writing its *Slik
   løser du det* text, **Then** the build fails and says which code is missing.
3. **Given** the Kontroller tab's "Hva betyr kodene?" explainer, **When** the user wants more, **Then** a
   *Les mer i Læringssenteret* link opens the Kontrollfunn reference.

---

### User Story 3 — All existing guidance is in one place and up to date (Priority: P2)

Everything in `docs/brukerveiledning.md` is moved into the learning center as short, task-titled articles,
checked against the app as it is today, and the old file becomes a pointer. The sections *Slik gjør du*
(one task per article, "Slik …"), *Når noe går galt* (one symptom per article, titled by what the user sees)
and *Forstå systemet* (the why: how invoices are grouped, price version validity, threshold, traceability,
correction runs) are filled.

**Why this priority**: Story 1 covers the path through a month. This story covers everything off that path
and removes the second copy that drifts. It is mostly writing, so it can follow the first delivery.

**Independent Test**: Go through each section of `docs/brukerveiledning.md` and find where its content now
lives in the learning center; check each article against the app in tt02.

**Acceptance Scenarios**:

1. **Given** the migration is done, **When** a reviewer goes through every section of the old user guide,
   **Then** each piece of guidance is found in an article, or is consciously dropped because the app no
   longer behaves that way.
2. **Given** the old guide described something the app no longer does (the Kunder menu and list, six
   products), **When** the matching article is written, **Then** it describes the app as it is now.
3. **Given** the user opens *Jeg vil …* on the start page, **When** they pick a task such as "endre tekstene
   på fakturaen", **Then** a single article answers it from start to finish.
4. **Given** the user sees a symptom such as "Godkjenn er grå", **When** they open *Når noe går galt*,
   **Then** an article titled by that symptom explains the causes and what to do for each.
5. **Given** `docs/brukerveiledning.md` after the migration, **When** someone opens it on GitHub, **Then**
   it says the guide now lives in the app under Hjelp and where the source files are.

---

### User Story 4 — Help where the user stands, and search (Priority: P3)

A user on the Bruksdata page is unsure what to do. A *Hjelp til denne siden* link on the page opens the
article for that page. Elsewhere, a user who only remembers a word ("terskelverdi") types it into a search
field in the learning center and gets the matching articles.

**Why this priority**: It shortens the way to help, but Stories 1–3 already make the help exist and
findable through the menu.

**Independent Test**: From each main page of the app, follow *Hjelp til denne siden* and confirm it lands on
the right article; search for ten common terms and confirm each finds the expected article first.

**Acceptance Scenarios**:

1. **Given** any main page of the app (Produkter, Prisversjoner, Bruksdata, Kjøringer and its tabs, Kunde,
   Hendelseslogg), **When** the user presses *Hjelp til denne siden*, **Then** the article about that page
   opens.
2. **Given** the learning center is open, **When** the user searches for a word that appears in an article's
   title, summary or text, **Then** the matching articles are listed, title matches first, each with its
   summary.
3. **Given** a search finds nothing, **When** the result is shown, **Then** it says so and points to the
   start page and *Når noe går galt*.

---

### Edge Cases

- **A page the article links to is removed or renamed**: the build fails, naming the article and the link
  (FR-016). The customer list removed on 2026-11-11 is the example this guards against.
- **A link between articles points to an article that does not exist**: the build fails (FR-015).
- **An article file exists but is not in the section menu, or appears twice**: the build fails (FR-014), so
  nothing is unreachable or duplicated.
- **An article uses a role-restricted action**: it says which role is needed; the article itself is still
  readable by every role.
- **The user opens an address under `/hjelp` that does not exist**: they get the app's normal "not found"
  page with a link to the Læringssenter start page.
- **Unconfirmed business facts** (open questions in docs/07, such as the invoice text limits in OQ-23):
  articles say the fact is not yet confirmed and name the open question; they never state a guess as fact.
- **Content with raw HTML or styling**: it is not rendered as markup, so an article cannot break the page's
  look or the app's UI rules.
- **Printing an article**: it prints without the top bar, the side columns or buttons, like other pages.
- **Test-environment-only functions** (*Nullstill periode*): the article says they exist only in tt02 and
  locally.

## Requirements *(mandatory)*

### Functional Requirements

**Entry and layout**

- **FR-001**: Every page in the app MUST show a **Hjelp** link at the right end of the top bar, separate from
  the main menu, that opens the Læringssenter start page at `/hjelp`. Inside the learning center the link is
  marked as the current section.
- **FR-002**: The start page MUST show the four entry points (*Ny her? Start her*, *Månedsrutinen steg for
  steg*, *Noe gikk galt*, *Oppslag*) and a *Jeg vil …* list of 10–15 common tasks, each linking to its
  article.
- **FR-003**: Every learning-center page MUST have three areas: a left section menu, the article in the
  middle with breadcrumbs, and a right column with *På denne siden*, *Gå til i appen* (when the article is
  about a page) and *Relatert* (when the article names related articles).
- **FR-004**: The section menu MUST list the sections in this order: Kom i gang, Månedsrutinen, Slik gjør du,
  Når noe går galt, Oppslag, Forstå systemet. The section holding the current article is open, and the
  current article is marked for screen readers as well as visually.
- **FR-005**: On narrow screens the section menu MUST fold into an expandable *Innhold* above the article,
  and the right column MUST move below the article, with no sideways scrolling.
- **FR-006**: Learning-center pages MUST follow the app's existing UI rules: Norwegian, the shared top bar,
  a skip link to the main content, the OS colour scheme, Designsystemet components, no inline styling, and no
  assets loaded from the internet.

**Articles**

- **FR-007**: Every article MUST have a title, a one-sentence summary shown under the title and in lists,
  and a "last updated" date shown at the bottom.
- **FR-008**: An article MAY name a role needed for its task; it is then shown as a tag near the title (for
  example "Krever rollen GODKJENNER").
- **FR-009**: Articles MUST support callouts of three kinds (*Tips*, *Viktig*, *Advarsel*), tables, numbered
  steps, and button names marked as buttons. Raw HTML in content MUST NOT be rendered as markup.
- **FR-010**: The Månedsrutinen section MUST be an ordered path of six steps (Sjekk registrene, Hent
  bruksdata, Generer kjøringen, Gå gjennom kontrollfunn, Godkjenn, Eksporter og send videre). Each step
  shows its position and a progress indicator, links to the previous and next step, and uses the parts
  *Hvem*, *Før du starter*, *Gjør slik*, *Slik ser du at det gikk bra*, *Hvis noe går galt*.
- **FR-011**: The lifecycles of runs and price versions MUST be shown as simple diagrams or state rows that
  work without loading any outside script.

**Reference generated from the app**

- **FR-012**: The Kontrollfunn reference MUST list every control code the app can raise, with the severity
  and definition taken from the same source the Kontroller tab uses, plus a hand-written *Slik løser du
  det* per code.
- **FR-013**: The Kontroller tab's "Hva betyr kodene?" and "Hva betyr alvorlighet?" explainers MUST link to
  the Kontrollfunn reference.

**Guarding against drift (all checked in the automated build)**

- **FR-014**: Every article MUST appear exactly once in the section menu, and every menu entry MUST point to
  an existing article.
- **FR-015**: Every link from one article to another MUST point to an existing article.
- **FR-016**: Every link from an article into the app (in *Gå til i appen* or in the text) MUST point to a
  page the app actually serves.
- **FR-017**: Every control code MUST have a *Slik løser du det* text (see User Story 2, scenario 2).
- **FR-018**: The project's definition of done MUST say that a change to something users see includes the
  matching learning-center update in the same pull request.

**Content (phase 2)**

- **FR-019**: All guidance in `docs/brukerveiledning.md` MUST be moved into articles and checked against the
  current app. The user-facing parts of the runbook's monthly procedure are merged into Månedsrutinen.
  Afterwards `docs/brukerveiledning.md` only points to the learning center.
- **FR-020**: Correction runs (*korrigeringskjøring*) MUST get their own how-to and explanation articles,
  stating clearly which parts are not yet available in production (OQ-6).

**Help in context and search (phase 3)**

- **FR-021**: Each main page of the app MUST show a *Hjelp til denne siden* link to its article.
- **FR-022**: The learning center MUST offer a search over article titles, summaries and text, listing
  title matches first, and an empty result that points to the start page and *Når noe går galt*.

**Access**

- **FR-023**: The learning center MUST be readable by every signed-in user (LESER and up) and MUST NOT
  offer any action that changes data.

### Key Entities

- **Artikkel**: one help page. Title, summary, section, optional role, optional app page it is about,
  optional related articles, last-updated date, and the body text.
- **Seksjon**: one of the six top-level groups in the section menu, holding articles in a set order.
- **Sti** (path): an ordered sequence of articles with previous/next and a progress indicator; Månedsrutinen
  is the first.
- **Kontrollkode** (existing): the app's list of control codes with severity and definition; the learning
  center adds a *Slik løser du det* text per code.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person who has never used forsystem generates, approves and exports a run for one test month
  in tt02 using only the learning center, without asking anyone, in under 45 minutes.
- **SC-002**: From any page in the app, the user reaches the right Månedsrutinen step or Kontrollfunn entry
  in at most three clicks.
- **SC-003**: 100 % of control codes the app can raise are explained in the learning center, and this stays
  true: a new code without an explanation cannot be released.
- **SC-004**: 0 broken links, between articles or into the app, in any released version.
- **SC-005**: After phase 2, there is exactly one maintained user guide (the learning center), and a reviewer
  finds no description in it that contradicts the app in tt02.
- **SC-006**: After phase 3, ten common terms (for example terskelverdi, kontering, LG04, forkast,
  fakturareferanse) each find the expected article as the first result.
- **SC-007**: Økonomi's reviewer judges the Månedsrutinen and Kontrollfunn articles correct and
  understandable before phase 1 is released.

## Delivery

Three pull requests, each releasable on its own:

1. **Phase 1** (User Stories 1 and 2): the learning center itself, the Hjelp link, Kom i gang (4 articles),
   Månedsrutinen (6 steps), the Kontrollfunn reference, and the drift checks (FR-001 – FR-018, FR-023).
2. **Phase 2** (User Story 3): Slik gjør du, Når noe går galt, the rest of Oppslag, Forstå systemet,
   retiring `docs/brukerveiledning.md` (FR-019, FR-020).
3. **Phase 3** (User Story 4): *Hjelp til denne siden* and search (FR-021, FR-022).

## Assumptions

- Users already sign in to forsystem; the learning center reuses that and adds no role.
- The content author for the first drafts is the development team; Økonomi reviews for correctness (SC-007).
- About 45 articles in total, most short; the start page's *Jeg vil …* list is chosen from the most common
  questions seen in the BF1 test round and kickoff.
- Articles describe tt02 and production alike; where they differ (test tools), the article says so.
- **Dependency, customer page:** since 2026-11-11 no page links to a customer's detail page. The *Finn og
  åpne en kunde* article is written once it is decided how users reach a customer; until then phase 2 leaves
  it out and says so in the review.
- **Dependency, front page (specs/015):** the front-page redesign may add a *Ny her?* card pointing to
  `/hjelp`. Both features change the shared top bar or start page, so they merge one after the other.
- Out of scope: screenshots and video, per-user progress tracking, feedback buttons, editing in the browser,
  English content, operations and developer documentation.
