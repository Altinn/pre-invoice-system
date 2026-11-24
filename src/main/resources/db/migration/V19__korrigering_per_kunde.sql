-- Corrections per customer (specs/019 stories 1-3).
-- One row per customer (orgnr) with any deviation on a comparison, including a changed fakturareferanse,
-- bestillingsnummer, kundenummer or name with transaction difference 0. The invoiced values are the distinct
-- values on the latest exported invoice(s) for the orgnr ('' = none); the candidate values are what the new
-- DWH snapshot delivered (NULL = the candidate has no customer row, e.g. the customer was removed).
create table bruksdata_kundeavvik (
    id                          bigint generated always as identity primary key,
    sammenligning_id            bigint not null references bruksdata_sammenligning,
    organisasjonsnummer         text not null,
    fakturert_navn              text[] not null default '{}',
    fakturert_kundenummer       text[] not null default '{}',
    fakturert_fakturareferanse  text[] not null default '{}',
    fakturert_bestillingsnummer text[] not null default '{}',
    kandidat_navn               text,
    kandidat_kundenummer        text,
    kandidat_fakturareferanse   text,
    kandidat_bestillingsnummer  text,
    kategorier                  text[] not null,
    unique (sammenligning_id, organisasjonsnummer),
    check (cardinality(kategorier) > 0),
    check (kategorier <@ array['LAGT_TIL', 'ENDRET', 'FJERNET',
                               'ENDRET_FAKTURAREFERANSE', 'ENDRET_BESTILLINGSNUMMER',
                               'ENDRET_KUNDENUMMER', 'ENDRET_NAVN']::text[])
);

-- Customers without any deviation, hidden on the page (FR-007). NULL on comparisons made before V19.
alter table bruksdata_sammenligning add column uendrede_kunder integer check (uendrede_kunder >= 0);

alter table bruksdata_endring add column kundeavvik_id bigint references bruksdata_kundeavvik;

-- Existing comparisons: one customer per (comparison, orgnr) of their changes, categories from the product
-- changes only. They were computed without customer values, so they get no reference categories.
insert into bruksdata_kundeavvik (sammenligning_id, organisasjonsnummer, kategorier)
select sammenligning_id, organisasjonsnummer,
       array_agg(distinct case endringstype when 'TILAGT' then 'LAGT_TIL' else endringstype end
                 order by case endringstype when 'TILAGT' then 'LAGT_TIL' else endringstype end)
  from bruksdata_endring
 group by sammenligning_id, organisasjonsnummer;

update bruksdata_endring e
   set kundeavvik_id = k.id
  from bruksdata_kundeavvik k
 where k.sammenligning_id = e.sammenligning_id
   and k.organisasjonsnummer = e.organisasjonsnummer;

alter table bruksdata_endring alter column kundeavvik_id set not null;
create index bruksdata_endring_kundeavvik on bruksdata_endring (kundeavvik_id);
