# Research: Clearer texts, labels and number format

## R1 — One formatter for every number

- **Decision**: Add a `@Component("tall")` bean, `Tallformat`, with four methods. Templates call it as
  `${@tall.belop(x)}`. Null renders as «—».
  - `belop(BigDecimal)`: two decimals, rounded half up.
  - `antall(Number)`: whole, with no decimals.
  - `mengde(BigDecimal)`: usage quantity, two decimals.
  - `pris(BigDecimal)`: all stored decimals, at least two, with trailing zeros beyond two stripped.
- **Rationale**: The templates mix `#numbers.formatDecimal(x, 1, 'WHITESPACE', 2, 'COMMA')` (about 15
  places) with raw `${x}` output, which prints `1234.50`. One bean makes the format testable once,
  and makes a later change, such as a non-breaking space, a one-line edit.
- **Separator**: a plain space (U+0020), as the existing `formatDecimal` calls produce, so existing
  assertions and copy-paste into Excel behave as today. Number cells already sit in right-aligned
  table columns.
- **Alternatives**: Keep `#numbers.formatDecimal` everywhere. Rejected: verbose, and it cannot express
  "all decimals, at least two" for prices. A Spring `Formatter` registered for `BigDecimal`. Rejected:
  it would also change form input binding and LG04-adjacent text.

## R2 — Control-code definitions live on the enum

- **Decision**: Give `Kontrollkode` two fields:
  - `alvorlighet` (the severity it is always raised with);
  - `definisjon` (one Norwegian sentence).

  The Kontroller tab renders the explanation from `Kontrollkode.values()`. A unit test fails if any
  code lacks a definition, and the service's `blokkerende(...)`/`advarsel(...)` helpers assert that
  the code's severity matches.
- **Rationale**: FR-008 requires the list to be complete. A hand-written list in the template would
  drift the next time a code is added (`DELT_REFERANSE` was added in specs/008).
- **UI**: two `<details>` disclosures, «Hva betyr alvorlighet?» and «Hva betyr kodene?», linked from
  the Alvorlighet and Kode column headers via `aria-describedby` and an anchor. Hover-only tooltips
  were rejected as inaccessible on touch and keyboard. A disclosure satisfies "trykke på" from the
  review.

## R3 — Readable usernames from Entra ID

- **Decision**: Set `spring.security.oauth2.client.provider.entra.user-name-attribute:
  preferred_username` in `application-prod.yaml`.
- **Rationale**: `BrukerContext.naavaerendeBruker()` returns `Authentication.getName()`. For an
  `OidcUser`, that is the claim named by `user-name-attribute`, which defaults to `sub`, an opaque
  per-app id. `preferred_username` is the UPN, normally the e-mail address. It is requested by the
  `profile` scope we already ask for. No code changes.
- **Consequence**: Rows already stored with a `sub` keep it. Nothing joins on the username, because
  roles come from groups (`EntraGruppeRolleMapper`), so a change of identifier breaks no access.
- **Alternatives**: Map `sub` to a name at display time. Rejected: it needs a user directory lookup
  (Graph API), which is an Azure SDK call outside an adapter, for no gain.

## R4 — Warning wording for «Hent fra datavarehus»

- **Decision**: «Ved å trykke på Hent fra datavarehus hentes bruksdata for perioden og vises for
  kontroll. Når importen er bekreftet, kan den ikke slettes eller endres.»
- **Rationale**: Fetching only stages a preview (status MOTTATT). Økonomi asked for an "equivalent"
  text, but it must not claim fetching is final.

## R5 — The invoice's reference on its detail page

- **Decision**: Show the distinct `(fakturareferanse, bestillingsnummer)` pairs of the invoice's
  lines once, in the header paragraph. Remove the per-line column.
- **Rationale**: An invoice usually has one pair. When it has several (`DELT_REFERANSE`), all are
  listed, matching the warning.
