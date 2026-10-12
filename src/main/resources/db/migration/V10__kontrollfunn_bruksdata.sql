-- specs/002-pre-approval-control D2: which usage rows a blocking finding kept off the invoice.
--
-- The generator skips a usage row at the first rule it fails (no customer, no kundenummer rule, no
-- price) and records one aggregated finding per orgnr / orgnr+product / product. This table keeps the
-- link from that finding to every row it skipped, so the reconciliation view (BF1 T06/T10) can show
-- the reason structurally instead of parsing messages. Rows with an inactive agreement or missing
-- kontering are not skipped (their lines exist; approval is blocked), so they have no link.
create table kontrollfunn_bruksdata (
    kontrollfunn_id bigint not null references kontrollfunn,
    bruksdata_id    bigint not null references bruksdata,
    primary key (kontrollfunn_id, bruksdata_id)
);
create index kontrollfunn_bruksdata_bruksdata on kontrollfunn_bruksdata (bruksdata_id);
