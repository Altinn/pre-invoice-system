-- Invoice texts per run (specs/009-kjoring-fakturatekster, docs/07 OQ-23): the LG04 header
-- (long_info1), footer (long_info2) and «Vår referanse» (responsible/responsible2), stored with the
-- run so the export writes what was approved. Limits are the LG04 field widths.
alter table fakturakjoring
    add column topptekst text check (char_length(topptekst) <= 120),
    add column bunntekst text check (char_length(bunntekst) <= 120),
    add column var_referanse text check (char_length(var_referanse) <= 25);

-- Runs that can still be exported get Økonomi's defaults (2026-11-04). Exported and discarded runs
-- keep NULL: their files were written with the old per-invoice link and configured responsible, and
-- storing today's defaults on them would misstate what was sent. The month name matches
-- Periode.norskLabel(); to_char's TMMonth is avoided because it depends on the server's lc_time.
update fakturakjoring
   set topptekst = 'Bruk av Altinn '
           || (array['januar', 'februar', 'mars', 'april', 'mai', 'juni', 'juli', 'august',
                     'september', 'oktober', 'november', 'desember'])[extract(month from periode)::int]
           || ' ' || extract(year from periode)::int,
       bunntekst = 'Se samarbeid.digdir.no for priser og innsyn i egen statistikk',
       var_referanse = 'Servicedesk@altinn.no'
 where status in ('GENERERT', 'GODKJENT');

alter table fakturakjoring add constraint fakturakjoring_tekster_satt check (
    status not in ('GENERERT', 'GODKJENT')
    or (topptekst is not null and bunntekst is not null and var_referanse is not null));
