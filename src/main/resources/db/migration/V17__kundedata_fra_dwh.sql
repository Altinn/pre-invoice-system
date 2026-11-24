-- specs/018-fjern-kunderegister, step 1 of 2 (additive). Customer data comes from CRM through the DWH;
-- forsystem keeps no registry of its own. Each import keeps the customer values it delivered per
-- organisation (research D1), and an invoice identifies its customer by orgnr instead of a registry link
-- (D5). V18 drops kunde_id and the registry tables once no code reads them.

-- The customer values one import delivered for one organisation. Insert-only like bruksdata and
-- bruksdata_kilde; a superseded import keeps its rows. `motstridende` lists values that disagreed between
-- the import's rows (e.g. 'kundenummer: 5663 | 5664'); generation blocks such an organisation (D2).
create table bruksdata_kunde (
    id                  bigint generated always as identity primary key,
    import_id           bigint not null references bruksdata_import,
    organisasjonsnummer text not null,
    virksomhetsnavn     text,
    kundenummer         text,
    fakturareferanse    text,
    bestillingsnummer   text,
    motstridende        text[] not null default '{}',
    constraint bruksdata_kunde_unik unique (import_id, organisasjonsnummer)
);

-- Imports made before this change carry no customer values. Take them from the registry as it is now, so a
-- regenerated old period keeps its customers: the customer default reference, and the kundenummer of the
-- rule without product (else the lowest rule). Orgnrs without a customer get an empty row, which gives
-- MANGLER_KUNDENUMMER like MANGLER_KUNDE did.
insert into bruksdata_kunde (import_id, organisasjonsnummer, virksomhetsnavn, kundenummer, fakturareferanse,
                             bestillingsnummer)
select o.import_id, o.organisasjonsnummer, k.virksomhetsnavn,
       (select r.kundenummer from kundenummer_regel r
         where r.kunde_id = k.id
         order by (r.produkt_id is not null), r.id
         limit 1),
       ref.fakturareferanse, ref.bestillingsnummer
  from (select distinct import_id, organisasjonsnummer from bruksdata) o
  left join kunde k on k.organisasjonsnummer = o.organisasjonsnummer
  left join kunde_referanse ref on ref.kunde_id = k.id and ref.produkt_id is null;

-- The invoice's own orgnr and name, so nothing reads the registry after generation (FR-013).
alter table faktura add column organisasjonsnummer text;
update faktura f
   set organisasjonsnummer = k.organisasjonsnummer,
       kundenavn = coalesce(f.kundenavn, k.virksomhetsnavn)
  from kunde k
 where k.id = f.kunde_id;
alter table faktura
    alter column organisasjonsnummer set not null,
    alter column kundenavn set not null,
    alter column kunde_id drop not null,
    add constraint faktura_unik_orgnr unique (kjoring_id, organisasjonsnummer, kundenummer);

alter table faktura_under_terskel
    alter column kunde_id drop not null,
    add constraint faktura_under_terskel_unik_orgnr unique (kjoring_id, organisasjonsnummer, kundenummer);
