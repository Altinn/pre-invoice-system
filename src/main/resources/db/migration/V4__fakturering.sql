-- Runs, invoices, lines, controls, exports. Design decisions preserved from docs/03:
--  * traceability is structural (fakturalinje -> bruksdata + pris; snapshot columns on faktura);
--  * invoices group by kundenummer, not by customer (unique (kjoring_id, kunde_id, kundenummer));
--  * run idempotency via a partial unique index (several runs per period only if older FORKASTET);
--  * ordre_nr lives on faktura, so a faktura can be split into several LG04 orders at export (OQ-1).

create table fakturakjoring (
    id             bigint generated always as identity primary key,
    periode        date not null check (extract(day from periode) = 1),
    status         text not null default 'GENERERT'
        check (status in ('GENERERT', 'GODKJENT', 'EKSPORTERT', 'FORKASTET')),
    prisversjon_id bigint not null references prisversjon,
    generert_av    text not null,
    generert_at    timestamptz not null default now(),
    godkjent_av    text,
    godkjent_at    timestamptz,
    kommentar      text
);
create unique index en_aktiv_kjoring_per_periode
    on fakturakjoring (periode) where status <> 'FORKASTET';

create table faktura (
    id                    bigint generated always as identity primary key,
    kjoring_id            bigint not null references fakturakjoring,
    kunde_id              bigint not null references kunde,
    faktura_uuid          uuid not null unique,   -- UUIDv7; used in PDF filenames
    ordre_nr              integer not null,       -- order_id within the LG04 file
    kundenummer           text not null,          -- snapshot at generation
    tilleggstekst         text,
    fakturamottaker_orgnr text not null,          -- snapshot at generation
    sum_belop             numeric(12,2) not null,
    unique (kjoring_id, kunde_id, kundenummer),
    unique (kjoring_id, ordre_nr)
);

create table fakturalinje (
    id                bigint generated always as identity primary key,
    faktura_id        bigint not null references faktura,
    produkt_id        bigint not null references produkt,
    beskrivelse       text not null,              -- art_desc: 'Bruk av melding januar 2027'
    antall            numeric(14,2),
    enhetspris        numeric(12,4),
    belop             numeric(12,2) not null,
    pris_id           bigint references pris,       -- traceability to the price used
    bruksdata_id      bigint references bruksdata,  -- traceability to the usage basis
    servicekode       text,
    fakturareferanse  text,                         -- snapshot at generation
    bestillingsnummer text                          -- snapshot at generation
);

create table kontrollfunn (
    id          bigint generated always as identity primary key,
    kjoring_id  bigint not null references fakturakjoring,
    faktura_id  bigint references faktura,
    alvorlighet text not null check (alvorlighet in ('BLOKKERENDE', 'ADVARSEL')),
    kode        text not null,      -- 'MANGLER_KUNDENUMMER','INAKTIV_AVTALE','STORT_AVVIK',...
    melding     text not null
);

create table eksportfil (
    id           bigint generated always as identity primary key,
    kjoring_id   bigint not null references fakturakjoring,
    type         text not null check (type in ('LG04', 'PDF', 'CSV', 'XLSX')),
    filnavn      text not null,
    blob_url     text not null,               -- local path in dev, blob URL in prod
    sha256       text,
    opprettet_av text not null,
    opprettet_at timestamptz not null default now()
);
