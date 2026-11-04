# Contract: the LG04 file forsystem writes (Altinn format)

The golden file `src/test/resources/lg04/altinn-test3.txt` is the binding contract (constitution II).
This page explains it field by field. Offsets are 0-based characters; the LG04_55 spec numbers
positions from 1 (spec position = offset + 1).

## File

- Windows-1252, every line exactly 4324 characters, space-filled, each line ending in `\n`
  (also the last).
- Per order: 1 header line, then 2 lines per product. Orders follow invoice order.

## Fields on every line of an order

| Offset | Width | Field | Value |
|---|---|---|---|
| 881 | 25 | batch_id | buntnummer = export date `yyyyMMdd` |
| 906 | 25 | client | `AR` |
| 2493 | 4 | line_no | `0` header; `1`, `2`, `3`… per product (both lines of a pair) |
| 2509 | 120 | long_info1 | `Bruk av Altinn <måned år>`, cut at 120 |
| 2629 | 120 | long_info2 | configured per-invoice link, cut at 120 (content open) |
| 3130 | 15 | order_id | order number, left-aligned |
| 3145 | 2 | order_type | `FS` |
| 3410 | 1 | status | `N` |
| 3519 | 25 | terms_id | `30` |
| 3944 | 2 | trans_type | `42` |
| 4259 | 25 | voucher_type | `SO` |

## Header line only (line_no 0)

| Offset | Width | Field | Value |
|---|---|---|---|
| 25 | 25 | accountable | fakturareferanse (Deres referanse), abbreviated past 25 |
| 233 | 25 | apar_id | kundenummer |
| 2368 | 25 used (field is 100) | ext_ord_ref | bestillingsnummer, abbreviated past 25 |
| 3264 | 25 | responsible | configured |
| 3289 | 25 | responsible2 | configured |

## Amount line (first line of a product pair)

| Offset | Width | Field | Value |
|---|---|---|---|
| 0 | 25 | account | product konto |
| 212 | 20 | amount | øre, right-aligned |
| 232 | 1 | amount_set | `1` |
| 538 | 255 | art_descr | product name |
| 793 | 25 | article | product artikkel-id |
| 965 | 20 | cur_amount | øre, right-aligned (same as amount) |
| 1908 / 1933 / 1983 | 25 | dim_1 / dim_2 / dim_4 | product dimensions |
| 4224 | 20 | value_1 | quantity × 100, right-aligned; **blank when no quantity** (Unit4 uses 1.0) |

## Text line (second line of a product pair)

| Offset | Width | Field | Value |
|---|---|---|---|
| 3314 | 8 | sequence_no | `8` (FEL's value; empty `short_info`) |

## Not written (deliberately)

`unit_code` (3946) — removed by FEL, the article's unit in Unit4 applies. `unit_price`, `text1`,
`short_info`, `period`, `order_date` — unused; see spec "Out of scope".
