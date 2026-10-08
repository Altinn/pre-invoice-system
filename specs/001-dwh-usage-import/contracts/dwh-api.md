# Contracts

## 1. Consumed: DWH `mv_altinn_usage_monthly` (Data API Builder)

Request (one per period, D4):

```
GET {forsystem.dwh.base-url}/api/mv_altinn_usage_monthly
    ?$filter=transaction_date ge 2026-09-01T00:00:00Z and transaction_date lt 2026-10-01T00:00:00Z
    &$first=100000
Authorization: Bearer <token>        # only when forsystem.dwh.auth=entra (OQ-19)
```

Response (200):

```json
{
  "value": [
    {
      "organisation_number": "999999999",
      "organisation_name": "testetat",
      "product_name": "Formidling",
      "quantity": 120,
      "amount_nok": null,
      "transaction_date": "2026-09-01",
      "source_table": "digdir_prod_broker_data",
      "fakturareferanse": "TEST0001",
      "kundenummer": "9999",
      "bestillingsnummer": null
    }
  ],
  "nextLink": "…"            // present ⇒ incomplete ⇒ forsystem refuses (R2)
}
```

forsystem's expectations (contract test pins these):

| Field | Required | forsystem rule |
|---|---|---|
| `value` | yes | array; empty ⇒ "ingen bruksdata ennå" |
| `nextLink` | no | any value ⇒ refuse whole fetch (until OQ-19b fixed) |
| `organisation_number` | yes | 9 digits else reject |
| `product_name` | yes | must exist in `produkt_kildenavn` else reject |
| `quantity` | yes for BRUKSVOLUM | number ≥ 0, integral |
| `amount_nok` | yes for cost types | number ≥ 0; non-null on a volume mapping ⇒ reject |
| `transaction_date` | yes | `YYYY-MM-DD` inside the period else reject |
| `kundenummer`, `fakturareferanse` | no | cross-check only (warning) |
| other fields | no | ignored, preserved in raw payload |

Unknown extra fields are tolerated. A missing required field, non-JSON body or non-200 status fails
the fetch (nothing staged). Timeouts: connect 5 s, read 60 s (configurable).

## 2. Exposed: forsystem UI/API additions

| Method / path | Role | Behaviour |
|---|---|---|
| `POST /bruksdata/hent-dwh` (form: `periode`) | FORVALTER | Fetch + preview; preview stored in session as today; renders `bruksdata/forhandsvis` with source DWH |
| `POST /bruksdata/bekreft` | FORVALTER | Unchanged endpoint; commits CSV or DWH preview |
| `GET /bruksdata/{id}` | LESER | Existing detail; shows source, fetch time, checksum, warnings |
| `GET /bruksdata/{id}/raadata` | LESER | Downloads archived raw JSON (`application/json`) |
| `POST /bruksdata/{id}/bekreft` | FORVALTER | Confirms a staged (`MOTTATT`) import after re-validation |
| `GET/POST /produkter/kildenavn` | LESER / FORVALTER | List / maintain DWH product-name mappings |

## 3. Configuration

```yaml
forsystem:
  dwh:
    enabled: ${FORSYSTEM_DWH_ENABLED:false}   # FR-010
    base-url: ${DWH_BASE_URL:}
    entitet: mv_altinn_usage_monthly
    maks-rader: 100000
    auth: ${DWH_AUTH:none}                    # none | entra (workload identity, OQ-19)
    scope: ${DWH_SCOPE:}                      # e.g. api://<dab-app-id>/.default
    tilkobling-timeout: 5s
    les-timeout: 60s
    avviksgrense-prosent: 50
    planlegging:
      enabled: ${FORSYSTEM_DWH_PLANLEGGING:false}
      cron: "0 0 6 7 * *"                     # 06:00 Europe/Oslo on the 7th (OQ-4)
```
