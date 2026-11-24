-- specs/018-fjern-kunderegister, step 2 of 2. Customer data is mastered in CRM and reaches forsystem with the
-- usage (bruksdata_kunde, V17); nothing reads the registry any more. Invoices carry their own orgnr and name
-- since V17, and every registry change is kept in hendelseslogg (entities KUNDE, KUNDE_REFERANSE,
-- KUNDENUMMER_REGEL). Take a pg_dump of the three tables before deploying (docs/runbook.md).
alter table faktura drop column kunde_id;
alter table faktura_under_terskel drop column kunde_id;

drop table kunde_referanse;
drop table kundenummer_regel;
drop table kunde;
