# UI contract: specs/014

## Endpoints

| Method | Path | Role | Effect |
|---|---|---|---|
| GET | `/hendelseslogg?fra=&til=&bruker=&entitet=&handling=&side=` | any signed-in | page of 50, newest first |
| GET | `/hendelseslogg.xlsx?<same filters>` | any signed-in | every matching row; logged `LASTET_NED` |
| GET | `/hendelseslogg.csv?<same filters>` | any signed-in | same as CSV |

Dates are `yyyy-MM-dd`. Empty parameters mean «no filter».

## Page

- Intro: «Alle endringer, nyeste først. Filtrer og last ned som regneark.»
- Filter form (GET): Fra dato, Til dato, Bruker, Entitet (select, «Alle»), Handling (select, «Alle»),
  «Filtrer», «Nullstill filter».
- Line «Viser 1–50 av 1 234 hendelser» (or «Ingen hendelser passer filteret»).
- Download buttons «Last ned (XLSX)» / «CSV» with the current filters.
- Table: Tidspunkt (dd.MM.yyyy HH:mm:ss, Oslo), Bruker, Handling, Entitet, Id, Detaljer (readable).
- Paging: «Forrige» / «Side N av M» / «Neste», keeping the filters.
- Invalid range: alert «Fra-datoen er etter til-datoen.»
