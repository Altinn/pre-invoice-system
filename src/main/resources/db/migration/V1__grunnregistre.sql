-- Products and prices. Products/prices are the temporary master maintained in this system
-- (docs/01). Kontering columns (artikkel_id, konto, dim_*) stay NULL until Økonomi answers
-- OQ-2; the generation controls must treat NULL kontering as BLOKKERENDE (docs/03).

create table produkt (
    id          bigint generated always as identity primary key,
    kode        text not null unique,        -- 'melding','formidling','varsling','autorisasjon','studio','appinfra'
    navn        text not null,
    artikkel_id integer,                     -- Unit4 article; TODO(OQ-2)
    konto       text,                        -- kontering per product, not constants; TODO(OQ-2)
    dim_1       text,                         -- TODO(OQ-2)
    dim_2       text,                         -- TODO(OQ-2)
    dim_4       text,                         -- TODO(OQ-2)
    enhet       text not null default 'transaksjon',
    aktiv       boolean not null default true
);

create table prisversjon (
    id         bigint generated always as identity primary key,
    navn       text not null unique,         -- 'Prisliste 2027'
    gyldig_fra date not null,
    gyldig_til date,
    status     text not null default 'UTKAST'
        check (status in ('UTKAST', 'AKTIV', 'ARKIVERT'))
);

create table pris (
    id             bigint generated always as identity primary key,
    prisversjon_id bigint not null references prisversjon,
    produkt_id     bigint not null references produkt,
    enhetspris     numeric(12,4) not null,
    unique (prisversjon_id, produkt_id)
);
