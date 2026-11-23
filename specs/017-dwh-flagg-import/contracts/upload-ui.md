# UI contract: *Bruksdata → Last opp fil*

**Upload form**: unchanged (period + file). Error text for oversized files names the limit.

**Preview (`forhandsvis`)** for a flagged file, above the existing product sums:

| Line | Content |
|---|---|
| Brukt | rows, orgnr count, volume per product |
| Utenfor perioden | rows per month (not an error) |
| DPV (ikke fakturert her) | rows, volume per product |
| Utelatt av datavarehuset | rows per `regel` reason, volume per product; expandable top organisations incl. «ukjent orgnr» |
| Kontrollsum | brukt + utenfor + DPV + utelatt = totalt rader i fila |

Reject list and blokkeringer render as today. «Opprett manglende kunder» lists only organisations from used rows.

**Import detail (`/bruksdata/{id}`)**: each usage row expands to its source groups (kildetabell, ressurs,
dagsrader, sum, regel texts). Imports from files without flags show «ingen kildedetaljer».
