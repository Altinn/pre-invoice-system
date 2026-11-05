-- Invoice threshold (specs/011-terskelverdi, docs/07 OQ-22). A customer (organisasjonsnummer) whose
-- total for the month is not over the threshold gets no invoice — 40 kr ex. mva, as FEL's program does
-- for DPI/eSignering and Økonomi decided for FinMod. The threshold belongs to the price version: set
-- while UTKAST, locked on activation, so a period is always generated with the same rule. NULL means
-- no threshold.
alter table prisversjon add column terskelverdi numeric(12,2) check (terskelverdi >= 0);
update prisversjon set terskelverdi = 40.00;

-- The would-be invoices a run left out, one per (customer, kundenummer), so Økonomi can see and
-- export them before approval. They have no faktura row, so nothing downstream (LG04, PDF, sums) sees
-- them. Each points at the run's UNDER_TERSKEL finding, whose kontrollfunn_bruksdata rows explain the
-- left-out usage in the usage reconciliation.
create table faktura_under_terskel (
    id                  bigint generated always as identity primary key,
    kjoring_id          bigint not null references fakturakjoring,
    kunde_id            bigint not null references kunde,
    organisasjonsnummer text not null,
    kundenavn           text not null,
    kundenummer         text not null,
    tilleggstekst       text,
    sum_belop           numeric(12,2) not null,
    antall_linjer       integer not null check (antall_linjer >= 1),
    kundesum            numeric(12,2) not null,
    terskelverdi        numeric(12,2) not null check (kundesum <= terskelverdi),
    kontrollfunn_id     bigint not null references kontrollfunn,
    unique (kjoring_id, kunde_id, kundenummer)
);
create index faktura_under_terskel_kjoring on faktura_under_terskel (kjoring_id);
