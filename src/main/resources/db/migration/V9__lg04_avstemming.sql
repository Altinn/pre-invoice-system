-- specs/002-pre-approval-control D6: the reconciliation of an exported LG04 file against its run.
--
-- Written once, in the export transaction, after the file was read back and matched the run (D3).
-- The check makes the guarantee structural: a mismatching reconciliation can never be stored, so a
-- row always means "the LG04 sent to regnskap has exactly the run's orders and amount" (BF1 T15).
create table lg04_avstemming (
    id                    bigint generated always as identity primary key,
    kjoring_id            bigint not null unique references fakturakjoring,
    eksportfil_id         bigint not null unique references eksportfil,
    antall_ordrer_fil     integer not null,
    sum_ore_fil           bigint  not null,
    antall_ordrer_kjoring integer not null,
    sum_ore_kjoring       bigint  not null,
    antall_fakturaer      integer not null,
    sha256                text    not null,   -- of the reconciled bytes; equals eksportfil.sha256
    avstemt_av            text    not null,
    avstemt_at            timestamptz not null default now(),
    check (antall_ordrer_fil = antall_ordrer_kjoring and sum_ore_fil = sum_ore_kjoring)
);
