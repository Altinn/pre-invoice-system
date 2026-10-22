# Quickstart: verifying the run tabs

1. Run `docker compose up --build`, or the local preview with the `demo` profile.
2. **Run list.** Generate 2027-01. The list row shows "Januar 2027", "Venter på godkjenning", the
   invoice count, the total and the finding counts.
3. **Oversikt.** The run opens on Oversikt. The tab bar shows Oversikt as current. The strip shows
   "Generert" done with user and time, and "Kontrollert og godkjent" current. The next-step panel
   and the summary follow.
4. **Kontroller.** The tab shows the findings, then the usage reconciliation. Opening
   `/kjoringer/1/avstemming` lands here, at the usage section.
5. **Fakturaer.**
   - Search for an orgnr from the list: one row, with the count and the sum.
   - Sort by Sum: the largest comes first.
   - The address has `q` and `sorter`. Reload: the same result.
6. **Approve and export** on Oversikt. The strip shows Godkjent, then Eksportert with who and when,
   and "Sendt til regnskap" becomes current.
7. **Filer** lists the downloads and export files. **Historikk** lists generated, approved and
   exported events, newest first.
8. **Width and theme.** At 375 px the tab bar scrolls sideways within itself, and the page doesn't.
   Dark mode is readable.
9. **Tests.** Run `./mvnw verify`; it should pass.
