-- Versioned, immutable correction runs (specs/007-korrigeringskjoring).
-- Ordinary imports/runs keep their existing behavior. A post-export DWH snapshot is stored as a
-- candidate and compared without replacing the VALIDERT import that V1 used.

alter table bruksdata_import drop constraint bruksdata_import_status_check;
alter table bruksdata_import add constraint bruksdata_import_status_check
    check (status in ('MOTTATT', 'VALIDERT', 'KANDIDAT', 'BEHANDLET', 'AVVIST'));

create unique index bruksdata_kandidat_payload
    on bruksdata_import (periode, raadata_sha256)
    where status in ('KANDIDAT', 'BEHANDLET');

alter table fakturakjoring
    add column type text not null default 'ORDINAER'
        check (type in ('ORDINAER', 'KORRIGERING')),
    add column versjon integer,
    add column forrige_kjoring_id bigint references fakturakjoring,
    add column korrigeringsaarsak text;

with nummerert as (
    select id,
           row_number() over (partition by periode order by generert_at, id)::integer as versjon,
           lag(id) over (partition by periode order by generert_at, id) as forrige_id
      from fakturakjoring
)
update fakturakjoring k
   set versjon = n.versjon,
       forrige_kjoring_id = n.forrige_id
  from nummerert n
 where n.id = k.id;

alter table fakturakjoring alter column versjon set not null;
alter table fakturakjoring add constraint fakturakjoring_versjon_positiv check (versjon >= 1);
alter table fakturakjoring add constraint fakturakjoring_periode_versjon_unique unique (periode, versjon);

drop index en_aktiv_kjoring_per_periode;
create unique index en_paagaaende_kjoring_per_periode
    on fakturakjoring (periode)
    where status in ('GENERERT', 'GODKJENT');

create table bruksdata_sammenligning (
    id                    bigint generated always as identity primary key,
    periode               date not null check (extract(day from periode) = 1),
    kandidat_import_id    bigint not null references bruksdata_import,
    grunnlag_kjoring_id   bigint not null references fakturakjoring,
    status                text not null
        check (status in ('AAPEN', 'KJORING_OPPRETTET', 'ERSTATTET', 'AVVIST')),
    uendrede_rader        integer not null check (uendrede_rader >= 0),
    opprettet_av          text not null,
    opprettet_at          timestamptz not null default now(),
    erstattet_av_id       bigint references bruksdata_sammenligning,
    unique (kandidat_import_id, grunnlag_kjoring_id)
);
create unique index en_aapen_sammenligning_per_periode
    on bruksdata_sammenligning (periode) where status = 'AAPEN';

create table bruksdata_endring (
    id                    bigint generated always as identity primary key,
    sammenligning_id      bigint not null references bruksdata_sammenligning,
    organisasjonsnummer   text not null,
    produkt_id            bigint not null references produkt,
    type                  text not null
        check (type in ('BRUKSVOLUM', 'AZURE_KOSTNAD', 'SMS_KOSTNAD')),
    endringstype          text not null check (endringstype in ('TILAGT', 'ENDRET', 'FJERNET')),
    gammel_bruksdata_id   bigint references bruksdata,
    ny_bruksdata_id       bigint references bruksdata,
    gammelt_antall        numeric(14,2),
    nytt_antall           numeric(14,2),
    delta_antall          numeric(14,2),
    gammelt_belop         numeric(12,2),
    nytt_belop            numeric(12,2),
    delta_belop           numeric(12,2),
    avvist_av             text,
    avvist_at             timestamptz,
    avvist_aarsak         text,
    unique (sammenligning_id, organisasjonsnummer, produkt_id, type),
    check (
        (type = 'BRUKSVOLUM'
            and delta_antall is not null and delta_antall <> 0
            and gammelt_belop is null and nytt_belop is null and delta_belop is null
            and coalesce(gammelt_antall, 0) + delta_antall = coalesce(nytt_antall, 0))
        or
        (type <> 'BRUKSVOLUM'
            and delta_belop is not null and delta_belop <> 0
            and gammelt_antall is null and nytt_antall is null and delta_antall is null
            and coalesce(gammelt_belop, 0) + delta_belop = coalesce(nytt_belop, 0))
    ),
    check (
        (endringstype = 'TILAGT' and gammel_bruksdata_id is null
            and gammelt_antall is null and gammelt_belop is null and ny_bruksdata_id is not null)
        or
        (endringstype = 'FJERNET' and ny_bruksdata_id is null
            and nytt_antall is null and nytt_belop is null)
        or
        (endringstype = 'ENDRET' and gammel_bruksdata_id is not null and ny_bruksdata_id is not null)
    ),
    check (
        (avvist_av is null and avvist_at is null and avvist_aarsak is null)
        or
        (avvist_av is not null and avvist_at is not null and length(btrim(avvist_aarsak)) > 0)
    )
);
create index bruksdata_endring_sammenligning on bruksdata_endring (sammenligning_id, id);
create index bruksdata_endring_filter on bruksdata_endring (sammenligning_id, produkt_id, endringstype);

create table korrigeringskjoring_endring (
    kjoring_id bigint not null references fakturakjoring,
    endring_id bigint not null references bruksdata_endring,
    valgt_av   text not null,
    valgt_at   timestamptz not null default now(),
    primary key (kjoring_id, endring_id)
);

alter table fakturakjoring
    add column sammenligning_id bigint references bruksdata_sammenligning;
alter table fakturakjoring add constraint fakturakjoring_type_grunnlag check (
    (type = 'ORDINAER' and sammenligning_id is null and korrigeringsaarsak is null)
    or
    (type = 'KORRIGERING' and sammenligning_id is not null and forrige_kjoring_id is not null
        and length(btrim(korrigeringsaarsak)) > 0)
);

alter table fakturalinje
    add column bruksdata_endring_id bigint references bruksdata_endring;
