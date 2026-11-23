-- specs/017-dwh-flagg-import: grouped source summary of an import's usage rows, so a figure can be
-- traced back to the DWH's source table and resource. Insert-only: a superseded import keeps its rows
-- (constitution III), like bruksdata itself.
create table bruksdata_kilde (
    id                  bigint generated always as identity primary key,
    import_id           bigint not null references bruksdata_import,
    organisasjonsnummer text not null,
    produkt_id          bigint not null references produkt,
    type                text not null
        check (type in ('BRUKSVOLUM', 'AZURE_KOSTNAD', 'SMS_KOSTNAD')),
    kildetabell         text,                   -- DWH source_table
    ressurs             text,                   -- resource_id without the urn:altinn:resource: prefix
    antall_dagsrader    integer not null check (antall_dagsrader > 0),
    antall              numeric,                -- summed quantity (volume types)
    belop               numeric(14,2),          -- summed amount (cost types)
    regel               text[] not null default '{}',  -- distinct regel texts, verbatim
    constraint bruksdata_kilde_unik
        unique nulls not distinct (import_id, organisasjonsnummer, produkt_id, type, kildetabell, ressurs)
);

create index bruksdata_kilde_import_org on bruksdata_kilde (import_id, organisasjonsnummer);
