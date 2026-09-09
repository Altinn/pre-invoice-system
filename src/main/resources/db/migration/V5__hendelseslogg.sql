-- Audit trail. Every state change (approve, price change, import, export) writes a row here
-- (docs/02). Free-form detaljer as jsonb keeps the schema stable as new handling types appear.

create table hendelseslogg (
    id         bigint generated always as identity primary key,
    tidspunkt  timestamptz not null default now(),
    bruker     text not null,
    handling   text not null,       -- 'GODKJENTE_KJORING','ENDRET_PRIS','IMPORTERTE_BRUKSDATA',...
    entitet    text,
    entitet_id bigint,
    detaljer   jsonb
);
