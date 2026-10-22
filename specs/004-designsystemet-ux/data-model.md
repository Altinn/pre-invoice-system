# Data model: Digdir look and feel with Designsystemet

No change. This feature touches templates and static assets only:

- no migration
- no new or changed entity, table or column
- no change to model attributes passed from controllers

The only new "data" is presentation state that lives in the markup:

- **Current section**: a constant string per page, passed to the header fragment
  (`produkter`, `prisversjoner`, `kunder`, `bruksdata`, `kjoringer`, `hendelseslogg`, or empty for
  the start page and the error pages).
- **Validation message**: browser-side only. Its text comes from a field's `data-feilmelding`
  attribute. It is never sent to the server.
