# Quickstart: verifying the run page quick wins

Run the app with `docker compose up --build`. The `demo` profile seeds usage for 2027-01.

1. **Run list.** On Kjøringer, generate 2027-01. The list says "Januar 2027", not 2027-01-01.
2. **GENERERT.** The run page opens with the "Kontroller og godkjenn" panel directly under the title,
   showing three checks and Godkjenn/Forkast. In a 1280×800 window, Godkjenn is visible without
   scrolling.
3. **Findings.** Under Kontrollfunn there is one row per code, blocking first. "Vis alle" expands
   the messages.
4. **Filer.** XLSX/CSV and "Avstemming bruksdata" sit together under Filer.
5. **GODKJENT.** Approve through the dialog. The panel becomes "Eksporter til regnskap" with who
   approved it and when.
6. **EKSPORTERT.** Export through the dialog. The panel shows the LG04 check and the "Til e-post til
   regnskap" figures with Kopier. Filer lists the export files and the LG04 re-check link.
7. **FORKASTET.** On a new period, Forkast, then "Generer januar 2027 på nytt". A new run opens.
8. **Sub-pages.** An invoice, the avstemming page and the LG04 check all show "Kjøring januar 2027"
   in the breadcrumbs.
9. **Tests.** Run `./mvnw verify`; it should pass.
