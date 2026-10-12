-- specs/002-pre-approval-control D1: keep superseded usage rows instead of deleting them.
--
-- Re-importing a period used to delete the period's bruksdata. Lines of a discarded (FORKASTET) run
-- still reference those rows, so the delete failed on fakturalinje_bruksdata_id_fkey and blocked the
-- correction flow (BF1 T08); it also erased history (constitution III). Rows of a superseded import
-- now stay, read-only, and uniqueness is per import rather than per period. "The period's usage" is
-- the rows of its current VALIDERT import (application logic).
alter table bruksdata
    drop constraint bruksdata_periode_organisasjonsnummer_produkt_id_type_key,
    add constraint bruksdata_unik_per_import unique (import_id, organisasjonsnummer, produkt_id, type);

-- D1: a run remembers the usage import it was built from. Backfilled from the run's lines; stays
-- NULL for older runs without lines.
alter table fakturakjoring
    add column bruksdata_import_id bigint references bruksdata_import;

update fakturakjoring k
   set bruksdata_import_id = (
       select min(b.import_id)
         from fakturalinje l
         join faktura f   on f.id = l.faktura_id
         join bruksdata b on b.id = l.bruksdata_id
        where f.kjoring_id = k.id);
