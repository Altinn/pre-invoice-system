# Quickstart: verify specs/010

## Automated

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"   # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
mvn verify
```

Key tests:

- `TallformatTest`: amounts, counts, quantities and prices, including negative values, zero, null
  and sub-øre prices.
- `KontrollkodeTest`: every code has a severity and a definition.
- `OidcBrukernavnTest`: prod config names `preferred_username`.
- The page ITs check the new wording and that no raw `1234.50`-style number is left on the pages
  they render.
- `Lg04SkriverTest.fileIsByteIdenticalToFelsSample`: still green.

## Manual (local demo)

1. Run `docker compose up --build` with the `local,demo` profile.
2. Open Bruksdata. Check the warning before «Hent fra datavarehus» and the next-step pointer to
   Kjøringer.
3. Upload or fetch a period and open the preview. Check that products show by name, numbers read
   «1 234,00», and the warning sits before «Bekreft import».
4. Open Kjøringer. Check the text at the top and the sum column format.
5. Generate a run and open Kontroller. Check:
   - both explanations open;
   - the column reads «Beskrivelse»;
   - Avstemming bruksdata has the intro text and the new column names.
6. Open one invoice. Check:
   - the customer name and reference are in the header;
   - there is no Ref. column;
   - the last row is the sum, eksl. mva.
7. Open Produkter. Check the headers read Dim1 Koststed, Dim2 Prosjekt/formål and Dim4 Kappost.
