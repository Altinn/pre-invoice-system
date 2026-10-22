# Quickstart: verifying the Designsystemet adoption

## Prerequisites

You need Docker running. With Colima, export `DOCKER_HOST` as described in the README.

## Run

```bash
docker compose up --build
```

Then open http://localhost:8080. The `demo` profile seeds a fake dataset.

## Automated

```bash
./mvnw verify
```

Expect:

- `TemplateKonvensjonTest` and `LayoutIT` pass.
- The LG04 golden-file tests pass unchanged.
- Every other existing test passes.

## Manual checks (light mode, then OS dark mode)

1. **Font and colours.** Open any page. In DevTools > Computed, `font-family` on body resolves to
   Inter. Primary buttons are Digdir blue (#0062BA in light mode). Network shows no request to any
   host but localhost.
2. **Header.** Open Kunder, then one customer. In both cases "Kunder" is marked current in the
   header. The customer page shows the breadcrumbs Forside › Kunder › *name*.
3. **Skip link.** Reload, press Tab. "Hopp til hovedinnhold" appears. Press Enter, and focus moves
   to the main content.
4. **Form validation.** On Kunder > Ny kunde:
   - Each hint is a separate line under its label, and the optional field shows "Valgfritt".
   - Type 8 digits into Organisasjonsnummer and submit. A red message appears under the field and
     nothing is sent.
   - Correct the value and the message disappears.
5. **Business-rule error.** Trigger one, for example by creating a customer with an existing
   orgnr. The error page shows a heading, a danger alert and a "Tilbake" button.
6. **Confirm dialog.**
   - Generate a run for 2027-01 on Kjøringer and open it.
   - Click Forkast: a dialog asks to confirm. Escape, or Avbryt, leaves the run GENERERT.
   - Open it again and confirm: the run becomes FORKASTET.
   - Repeat with Godkjenn on a new run. Eksporter is the same on a GODKJENT run.
7. **Tables.** On the run page, the Sum column is right-aligned with digits lined up, and hovering
   a row highlights it. Blocking findings and warnings show as alerts with the code as heading.
8. **Help text.** On Bruksdata, the long format explanations are collapsed under expandable
   sections.
9. **Narrow screen.** Set the window to about 375 px wide. The header wraps, breadcrumbs collapse
   to a back link, and only wide tables scroll sideways.
10. **Print preview.** Open print preview on the run page. There is no header, no breadcrumbs and
    no action buttons.
