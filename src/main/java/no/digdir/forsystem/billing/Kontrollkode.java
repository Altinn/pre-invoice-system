package no.digdir.forsystem.billing;

/**
 * Control finding codes (docs/04 Phase 3). Stored as text in {@code kontrollfunn.kode}. Each code carries the
 * severity it is always raised with and the definition the Kontroller tab shows (specs/010 FR-008), so a new
 * code cannot reach users unexplained.
 */
public enum Kontrollkode {
    MANGLER_KUNDE(Alvorlighet.BLOKKERENDE,
            "Det finnes bruk for et organisasjonsnummer som ikke er i kunderegisteret. Bruken er ikke fakturert."),
    MANGLER_KUNDENUMMER(Alvorlighet.BLOKKERENDE,
            "Kunden har ingen kundenummerregel som passer bruken, så fakturaen kan ikke adresseres i Unit4."),
    INAKTIV_AVTALE(Alvorlighet.BLOKKERENDE,
            "Kunden har bruk i perioden, men avtalestatusen er ikke aktiv."),
    MANGLER_PRIS(Alvorlighet.BLOKKERENDE,
            "Produktet har bruk, men ingen pris i den aktive prisversjonen. Bruken er ikke fakturert."),
    MANGLER_KONTERING(Alvorlighet.BLOKKERENDE,
            "Produktet mangler konto, dimensjoner eller artikkel-id i produktregisteret."),
    STORT_AVVIK(Alvorlighet.ADVARSEL,
            "Kundens sum avviker mer enn 30 % fra kjøringen for forrige måned."),
    NULLBELOP(Alvorlighet.ADVARSEL,
            "Fakturaen har minst én linje med beløp 0."),
    NY_KUNDE(Alvorlighet.ADVARSEL,
            "Kunden var ikke med i kjøringen for forrige måned, så summen kan ikke sammenlignes."),
    /** A customer with an active agreement has no usage in the period — an expected TE may be missing (BF1 T10). */
    AKTIV_KUNDE_UTEN_BRUK(Alvorlighet.ADVARSEL,
            "Kunden har aktiv avtale, men ingen bruk i perioden. Sjekk at ingen bruk mangler."),
    /** The invoice's lines carry different fakturareferanse/bestillingsnummer, so LG04 splits it into several orders. */
    DELT_REFERANSE(Alvorlighet.ADVARSEL,
            "Linjene har ulik fakturareferanse eller bestillingsnummer, så kunden får én faktura i Unit4 per referanse.");

    private final Alvorlighet alvorlighet;
    private final String definisjon;

    Kontrollkode(Alvorlighet alvorlighet, String definisjon) {
        this.alvorlighet = alvorlighet;
        this.definisjon = definisjon;
    }

    public Alvorlighet alvorlighet() {
        return alvorlighet;
    }

    public String definisjon() {
        return definisjon;
    }
}
