-- Temporary customer registry, used until CRM (D365) takes over (OQ-5). The `kilde` column
-- flips to 'CRM' when that happens; snapshots on faktura mean the switch never rewrites history.
-- Models the FinMod business rules from docs/01: deviating recipient, product-specific
-- references, and non-1:1 orgnr↔kundenummer (via kundenummer_regel).
-- `unique nulls not distinct` requires PostgreSQL 15+.

create table kunde (
    id                    bigint generated always as identity primary key,
    organisasjonsnummer   text not null unique check (organisasjonsnummer ~ '^[0-9]{9}$'),
    virksomhetsnavn       text not null,
    fakturamottaker_orgnr text,              -- deviating recipient: another legal entity (OQ-7)
    avtalestatus          text not null default 'AKTIV'
        check (avtalestatus in ('AKTIV', 'INAKTIV', 'UNDER_AVKLARING')),
    kilde                 text not null default 'MANUELL',   -- becomes 'CRM' when D365 takes over
    oppdatert_av          text not null,
    oppdatert_at          timestamptz not null default now()
);

create table kunde_referanse (
    id                bigint generated always as identity primary key,
    kunde_id          bigint not null references kunde,
    produkt_id        bigint references produkt,   -- null = customer default; product row overrides
    fakturareferanse  text,
    bestillingsnummer text,
    unique nulls not distinct (kunde_id, produkt_id)
);

create table kundenummer_regel (
    id            bigint generated always as identity primary key,
    kunde_id      bigint not null references kunde,
    kundenummer   text not null,               -- master: Unit4
    produkt_id    bigint references produkt,
    servicekode   text,                        -- e.g. Utdanningsdirektoratet: two servicekoder
    tilleggstekst text,                        -- e.g. Politiet: shared kundenummer, distinct text
    unique nulls not distinct (kunde_id, produkt_id, servicekode)
);
