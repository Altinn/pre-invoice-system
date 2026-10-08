-- Usage from the datavarehus API (specs/001-dwh-usage-import, docs/06 §1).
--
-- produkt_kildenavn: maps a source's own product name (the DWH view's `product_name`, e.g.
-- 'Varsling e-post') to a forsystem product and usage type. Several names may map to the same
-- product/type; their quantities are summed. Deliberately no seed rows: the mapping is a business
-- decision that is still open (OQ-18) — an unmapped name rejects the import instead of being guessed.
create table produkt_kildenavn (
    id         bigint generated always as identity primary key,
    kilde      text not null default 'DWH' check (kilde in ('DWH')),
    kildenavn  text not null,
    produkt_id bigint not null references produkt,
    type       text not null check (type in ('BRUKSVOLUM', 'AZURE_KOSTNAD', 'SMS_KOSTNAD')),
    unique (kilde, kildenavn)
);

-- A DWH import keeps the exact raw response it was built from (archived via FileArchive with its
-- SHA-256) so every line stays traceable even after the DWH view has moved on (OQ-20).
alter table bruksdata_import
    add column raadata_url    text,
    add column raadata_sha256 text,
    add column hentet_at      timestamptz,
    add constraint bruksdata_import_dwh_har_raadata
        check (kilde <> 'DWH' or (raadata_url is not null and raadata_sha256 is not null and hentet_at is not null));
