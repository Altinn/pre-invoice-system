-- Dismissing a customer on a comparison with a reason (specs/019 story 4, FR-015..FR-017).
-- The customer's product changes get the same values in their existing avvist_* columns (V12), so a
-- dismissed change can never be selected into a correction run. Restoring clears both.
alter table bruksdata_kundeavvik
    add column avvist_av     text,
    add column avvist_at     timestamptz,
    add column avvist_aarsak text,
    add constraint bruksdata_kundeavvik_avvist_hel check (
        (avvist_av is null and avvist_at is null and avvist_aarsak is null)
        or (avvist_av is not null and avvist_at is not null and length(btrim(avvist_aarsak)) > 0));
