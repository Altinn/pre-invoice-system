-- specs/002-pre-approval-control D9: the customer's name, frozen at generation like kundenummer and
-- fakturamottaker already are, so a run's spreadsheet, run page and PDF show the run as generated even if
-- the customer is renamed later. NULL for invoices generated before this column existed; views then show
-- the current registry name, marked "(nåværende navn)". Not part of LG04.
alter table faktura add column kundenavn text;
