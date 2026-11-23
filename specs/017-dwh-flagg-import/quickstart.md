# Quickstart: validating 017

Prereqs: `mvn verify` env as in the memory note (Colima `DOCKER_HOST`, JDK 21).

1. **Unit + IT**: `mvn verify -Dtest='DwhCsvFlaggTest,DwhFlaggImportIT' ` — fixture `src/test/resources/dwh/eksport-flagg-3m.csv`;
   asserts per-product totals for 2026-10 equal the independent sum of flag=1 rows, used+utenfor+DPV+utelatt = totalt,
   old fixtures `eksport-2026-09.csv` / `…transaction-month.csv` unchanged.
2. **Real file (local only, not committed)**: `docker compose up`, sign in as FORVALTER, map the eight product
   names under Produkter → Kildenavn, upload `~/Downloads/Mv_altinn_usage_last_3m_flag_20261106v2` for 2026-10.
   Expect: preview < 30 s, no rejects, «Brukt» ≈ 53 530-row month minus unused rows, kontrollsum balanced,
   `Opprett manglende kunder` offers only organisations with flagged usage.
3. Confirm import → open import detail → expand «Lillestrøm kommune / Autorisasjon»: groups sum to the usage row.
4. Repeat for 2026-08 and 2026-09 (re-import replaces per period; Autorisasjon absent in August is not an error).
5. Negative: edit one flag-1 row's orgnr to `12` → rejected with line number; set a flag to `2` → rejected.
