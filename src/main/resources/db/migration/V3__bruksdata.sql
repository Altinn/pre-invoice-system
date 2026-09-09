-- Usage input. CSV upload now, datavarehus later (OQ-4) — same tables, distinguished by `kilde`.
-- The natural key (periode, orgnr, produkt, type) supports the re-import replacement rule
-- (docs/03 decision 4): a new import for a period deletes the previous rows and marks the old
-- import AVVIST in application logic.

create table bruksdata_import (
    id           bigint generated always as identity primary key,
    filnavn      text not null,
    periode      date not null check (extract(day from periode) = 1),
    kilde        text not null default 'CSV' check (kilde in ('CSV', 'DWH')),
    status       text not null default 'MOTTATT'
        check (status in ('MOTTATT', 'VALIDERT', 'AVVIST')),
    antall_rader integer,
    lastet_av    text not null,
    lastet_at    timestamptz not null default now()
);

create table bruksdata (
    id                  bigint generated always as identity primary key,
    import_id           bigint not null references bruksdata_import,
    periode             date not null check (extract(day from periode) = 1),
    organisasjonsnummer text not null,         -- the usage-generating entity
    produkt_id          bigint not null references produkt,
    type                text not null
        check (type in ('BRUKSVOLUM', 'AZURE_KOSTNAD', 'SMS_KOSTNAD')),
    antall              numeric(14,2),         -- for BRUKSVOLUM
    belop               numeric(12,2),         -- for pass-through costs
    unique (periode, organisasjonsnummer, produkt_id, type)
);
