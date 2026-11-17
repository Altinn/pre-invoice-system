---
tittel: Endre en pris eller terskelverdien
sammendrag: Pris og terskelverdi kan bare endres i et utkast, så en aktiv versjon må erstattes av en ny.
oppdatert: 2026-11-17
rolle: FORVALTER
apne-i-appen: /prisversjoner
apne-i-appen-tekst: Åpne Prisversjoner
relatert: [slik-gjor-du/lag-prisversjon, forsta-systemet/terskelverdi, forsta-systemet/prisversjon-og-gyldighet]
nokkelord: [enhetspris, minstebeløp, bagatellgrense, låst]
---
Enhetspriser og terskelverdi hører til prisversjonen og låses når versjonen aktiveres. Da får alle kjøringer for en periode samme regler. Du trenger rollen FORVALTER.

## Hvis versjonen er et utkast

1. Åpne [Prisversjoner](/prisversjoner) og trykk [Åpne](knapp:) på utkastet.
2. **Pris:** skriv ny **Enhetspris** på produktets rad og trykk [Lagre](knapp:) på raden. Enhetspriser lagres med opptil fire desimaler.
3. **Terskelverdi:** skriv beløpet i feltet «Terskelverdi (kr eksl. mva per kunde per måned)» og trykk [Lagre terskelverdi](knapp:).

Du kan ikke lagre en negativ pris eller terskelverdi. Terskelverdien rundes til to desimaler.

## Hva terskelverdien gjør

Kunder med samlet beløp for måneden **lik eller under** terskelverdien får ingen faktura. Beløpet summeres per kunde (organisasjonsnummer) over alle produkter og kundenumre, eksl. mva. En ny versjon starter på 40,00 kr. Se [Terskelverdi](../forsta-systemet/terskelverdi.md) for hvordan den virker på kjøringen.

> [!IMPORTANT]
> Om terskelverdien skal gjelde under FinMod, og hvordan beløp under den håndteres, er ikke endelig avklart (OQ-22). Sjekk med Økonomi før du endrer den.

## Hvis versjonen er aktiv eller arkivert

Prisene vises, men feltene er låst. Slik endrer du dem:

1. Lag en ny prisversjon med riktig **Gyldig fra**, se [Lag en ny prisversjon](lag-prisversjon.md).
2. Sett prisene og terskelverdien i den nye versjonen.
3. Aktiver den.

To aktive versjoner kan ikke overlappe i tid. Dekker den gamle versjonen de samme datoene, må du arkivere den før du kan aktivere den nye. Sørg for at den nye versjonen dekker alle perioder du skal kjøre.

> [!WARNING]
> En kjøring som allerede er generert, må du forkaste og generere på nytt hvis den skal bruke nye priser. Se [Forkast og generer på nytt](forkast-og-generer-pa-nytt.md).

## Slik ser du at det gikk bra

Prisen eller terskelverdien står slik du skrev den etter at siden lastes på nytt. Endringen er logget i [Hendelseslogg](/hendelseslogg).
