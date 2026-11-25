-- Avregning (specs/020): a settlement run invoices the real transaction price of a year's usage. It is a run of its
-- own type, anchored on 1 January of the settlement year, that covers the months the user ticked. It sits outside the
-- per-month rules (one pending run per month, version per month): a settlement and the ordinary January run are
-- independent. It uses no price version — the price adjustment per product is what it bills with.

alter table fakturakjoring drop constraint fakturakjoring_type_check;
alter table fakturakjoring add constraint fakturakjoring_type_check
    check (type in ('ORDINAER', 'KORRIGERING', 'AVREGNING'));

alter table fakturakjoring drop constraint fakturakjoring_type_grunnlag;
alter table fakturakjoring add constraint fakturakjoring_type_grunnlag check (
    (type = 'ORDINAER' and sammenligning_id is null and korrigeringsaarsak is null)
    or
    (type = 'KORRIGERING' and sammenligning_id is not null and forrige_kjoring_id is not null
        and length(btrim(korrigeringsaarsak)) > 0)
    or
    (type = 'AVREGNING' and sammenligning_id is null and korrigeringsaarsak is null
        and forrige_kjoring_id is null and prisversjon_id is null)
);
alter table fakturakjoring alter column prisversjon_id drop not null;
alter table fakturakjoring add constraint fakturakjoring_prisversjon_krav
    check (type = 'AVREGNING' or prisversjon_id is not null);

alter table fakturakjoring drop constraint fakturakjoring_periode_versjon_unique;
create unique index fakturakjoring_periode_versjon_unique
    on fakturakjoring (periode, versjon) where type <> 'AVREGNING';
create unique index fakturakjoring_avregning_versjon_unique
    on fakturakjoring (periode, versjon) where type = 'AVREGNING';

drop index en_paagaaende_kjoring_per_periode;
create unique index en_paagaaende_kjoring_per_periode
    on fakturakjoring (periode)
    where status in ('GENERERT', 'GODKJENT') and type <> 'AVREGNING';
create unique index en_paagaaende_avregning_per_aar
    on fakturakjoring (periode)
    where status in ('GENERERT', 'GODKJENT') and type = 'AVREGNING';

-- The months a settlement run covers, and the price adjustment per product it was generated with.
create table avregning_maaned (
    kjoring_id bigint not null references fakturakjoring,
    periode    date not null check (extract(day from periode) = 1),
    import_id  bigint not null references bruksdata_import,
    primary key (kjoring_id, periode)
);

create table avregning_prisjustering (
    kjoring_id bigint not null references fakturakjoring,
    produkt_id bigint not null references produkt,
    justering  numeric(12,4) not null,    -- kr per unit, added to what the unit cost; may be negative
    primary key (kjoring_id, produkt_id)
);
