# Data Model: Acknowledge control findings

## V15__kontrollfunn_aksept.sql

```sql
alter table kontrollfunn
    add column akseptert_av text,
    add column akseptert_at timestamptz,
    add constraint kontrollfunn_aksept_hel check ((akseptert_av is null) = (akseptert_at is null)),
    add constraint kontrollfunn_aksept_bare_advarsel check (akseptert_at is null or alvorlighet = 'ADVARSEL');
```

Existing rows get null (not accepted). Runs already GODKJENT/EKSPORTERT are past the gate and stay as
they are; a GENERERT run needs its warnings accepted before approval.

## Kontrollfunn (record)

| Field | Type | Notes |
|---|---|---|
| akseptertAv | String | username from `BrukerContext`; null = not accepted |
| akseptertAt | OffsetDateTime | null = not accepted |

`erAkseptert()`, `medAksept(bruker, tid)` / `utenAksept()`. The six-argument constructor stays for
generation and tests.

## Hendelseslogg

| handling | entitet | entitet_id | detaljer |
|---|---|---|---|
| `AKSEPTERTE_FUNN` | FAKTURAKJORING | run id | `{periode, versjon, kode, antall, funnIder}` |
| `ANGRET_AKSEPT` | FAKTURAKJORING | run id | same |

## State rules

- Change only while the run is GENERERT, only ADVARSEL, only findings of that run.
- `godkjenn` refuses if any BLOKKERENDE exists or any ADVARSEL has `akseptert_at is null`.
- A regenerated run has new rows → none accepted.
