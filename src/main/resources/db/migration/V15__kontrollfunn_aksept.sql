-- Acknowledging control findings (specs/013-kvittering-kontrollfunn, Økonomi's kickoff row 34).
-- A warning (ADVARSEL) can be marked as accepted by a named user while the run is GENERERT; approval
-- waits until every warning is accepted. Blocking findings can never be accepted: they are fixed by
-- correcting the data and generating the run again. Each change is also logged in hendelseslogg
-- (AKSEPTERTE_FUNN / ANGRET_AKSEPT); these columns hold only the current state.
alter table kontrollfunn
    add column akseptert_av text,
    add column akseptert_at timestamptz,
    add constraint kontrollfunn_aksept_hel check ((akseptert_av is null) = (akseptert_at is null)),
    add constraint kontrollfunn_aksept_bare_advarsel check (akseptert_at is null or alvorlighet = 'ADVARSEL');
