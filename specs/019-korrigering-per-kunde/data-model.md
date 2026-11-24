# Data model: Corrections per customer (specs/019)

Sketch for `V19__korrigering_per_kunde.sql`. Builds on V12 (specs/007) and V17/V18 (specs/018).

```sql
-- One row per customer (orgnr) with any deviation on a comparison, including reference-only
-- changes with transaction difference 0 (specs/019 FR-004..FR-006).
create table bruksdata_kundeavvik (
    id                       bigint generated always as identity primary key,
    sammenligning_id         bigint not null references bruksdata_sammenligning,
    organisasjonsnummer      text not null,
    -- what the customer was invoiced with (latest exported invoice for the orgnr in the chain)
    fakturert_faktura_id     bigint references faktura,
    fakturert_navn           text,
    fakturert_kundenummer    text,
    fakturert_fakturareferanse   text[],   -- distinct invoiced values; null = unknown (old run)
    fakturert_bestillingsnummer  text[],
    -- what the candidate says now
    kandidat_kunde_id        bigint references bruksdata_kunde,
    kategorier               text[] not null,   -- subset of the enum below, never empty
    netto_belop_differanse   numeric(12,2),     -- null until priced; 0 for reference-only
    avvist_av                text,
    avvist_at                timestamptz,
    avvist_aarsak            text,
    unique (sammenligning_id, organisasjonsnummer),
    check (cardinality(kategorier) > 0),
    check (kategorier <@ array['LAGT_TIL','FJERNET','ENDRET',
                               'ENDRET_FAKTURAREFERANSE','ENDRET_BESTILLINGSNUMMER',
                               'ENDRET_KUNDENUMMER','ENDRET_NAVN']),
    check ((avvist_av is null and avvist_at is null and avvist_aarsak is null)
        or (avvist_av is not null and avvist_at is not null and length(btrim(avvist_aarsak)) > 0))
);

alter table bruksdata_endring
    add column kundeavvik_id bigint references bruksdata_kundeavvik;
-- back-fill: one kundeavvik per (sammenligning, orgnr) of existing changes, categories from
-- endringstype; existing comparisons get no reference categories (they were computed without them).

create table korrigeringskjoring_kunde (
    kjoring_id     bigint not null references fakturakjoring,
    kundeavvik_id  bigint not null references bruksdata_kundeavvik,
    primary key (kjoring_id, kundeavvik_id)
);

alter table fakturakjoring
    add column korrigeringsmetode text
        check (korrigeringsmetode in ('DELTA', 'KREDIT_OG_NY'));
update fakturakjoring set korrigeringsmetode = 'DELTA' where type = 'KORRIGERING';
alter table fakturakjoring add constraint fakturakjoring_metode check (
    (type = 'ORDINAER' and korrigeringsmetode is null)
    or (type = 'KORRIGERING' and korrigeringsmetode is not null));
-- KREDIT_OG_NY is refused by the service until OQ-6/OQ-29 are answered (TODO(OQ-6)).
```

Not added now (shape depends on OQ-29 c/h): the credited invoice number, the link from a credit
invoice to the invoice(s) it credits, and the pairing of credit and new invoice.

`ENDRET_NAVN` compares `faktura.kundenavn` with the candidate's `virksomhetsnavn`. Whether a name
change needs a correction is OQ-29 (f); it is a category so the change is never hidden (FR-005).
