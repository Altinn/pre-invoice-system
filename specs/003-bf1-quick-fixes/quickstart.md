# Quickstart: validating the BF1 quick fixes

```bash
./mvnw verify
```

New or extended tests:

- `PrisversjonServiceIT`:
  - `produkterFor` hides inactive products without a price and shows those with one
  - `dekning` covers open-ended, mid-month start, a closed range, and "no period"
- `ProduktServiceIT` or a web test: the list order is active first; dim labels with and without
  configuration.
- `GenereringControlsIT`:
  - `AKTIV_KUNDE_UTEN_BRUK` is raised for an active customer without usage
  - not raised for an inactive one, and not raised for a customer whose usage was blocked
  - it doesn't block approval
- `EksportIT` or `EksportAvstemmingIT`:
  - downloading an export file logs `LASTET_NED`
  - a file id from another run gets a 400 and no log entry

Manual (demo profile):
1. In *Produkter*, deactivate "studio". It moves to the bottom, and the Dim columns are visible.
2. In *Prisversjoner*, the help text is under the date fields. Create a draft from 2027-02-01: studio
   is not listed, and the page says "Brukes for kjøringer fra februar 2027 og fremover".
3. Add a customer with an active agreement and no usage, then generate 2027-01. The run shows the
   warning and can still be approved.
4. Export, download the LG04, and check that the *Hendelseslogg* shows `LASTET_NED` with type LG04.
