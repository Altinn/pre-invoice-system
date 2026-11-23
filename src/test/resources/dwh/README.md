# DWH fixtures

`eksport-flagg-3m.csv` (specs/017) is a 124-row cut of the datavarehus export
`Mv_altinn_usage_last_3m_flag_20261106v2` (113 357 rows, not committed). Rows are kept as in the export except that personal names and initials in `fakturareferanse` are replaced with fake references (`TESTREF-A` …); orgnr, products, quantities and flags are untouched.
Selection: per (product, flag_inv_altinn, flag_inv_dpv, regel[, month for flagged rows]) 1–2 rows; plus
every orgnr anomaly seen (`NULL`, empty, `unknown`, name), NULL quantity on flag-0 rows, `urn:altinn:resource:`
resources, appinfra amount rows for each month, and the repeated-key NAV SMS 2026-08-03 group; capped at 8
rows per (product, month, flags). Expected figures in tests are computed from the file itself
(`FlaggFiksturer`), never hard-coded.
