package no.digdir.forsystem.billing;

/**
 * Control finding codes (docs/04 Phase 3). Stored as text in {@code kontrollfunn.kode}. Each code carries the
 * severity it is always raised with and the definition the Kontroller tab shows (specs/010 FR-008), so a new
 * code cannot reach users unexplained.
 *
 * <p>Codes marked {@link #utgatt()} are no longer raised (specs/018 removed the customer registry they checked)
 * but stay so findings stored on older runs still render with their definition.
 */
public enum Kontrollkode {
    /** Retired by specs/018: there is no customer registry; customer data comes with the usage. */
    @Deprecated
    MANGLER_KUNDE(Alvorlighet.BLOKKERENDE,
            "Det fantes bruk for et organisasjonsnummer som ikke var i kunderegisteret. Kontrollen kjøres ikke "
                    + "lenger: kundedata kommer fra CRM via datavarehuset.", true),
    MANGLER_KUNDENUMMER(Alvorlighet.BLOKKERENDE,
            "Datavarehuset har ikke levert kundenummer for organisasjonen, så fakturaen kan ikke adresseres i Unit4. "
                    + "Bruken er ikke fakturert."),
    /** The usage rows of one organisation disagree on its customer values; forsystem does not pick one (specs/018). */
    MOTSTRIDENDE_KUNDEDATA(Alvorlighet.BLOKKERENDE,
            "Radene fra datavarehuset har ulikt kundenummer, fakturareferanse eller bestillingsnummer for samme "
                    + "organisasjon. Bruken er ikke fakturert."),
    /** Retired by specs/018: the DWH flag already leaves out usage without an active agreement (OQ-4, OQ-25). */
    @Deprecated
    INAKTIV_AVTALE(Alvorlighet.BLOKKERENDE,
            "Kunden hadde bruk i perioden, men avtalestatusen i kunderegisteret var ikke aktiv. Kontrollen kjøres "
                    + "ikke lenger: datavarehuset tar bare med bruk under aktiv avtale.", true),
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
    /** Retired by specs/018: without a registry there is no list of active customers to compare with. */
    @Deprecated
    AKTIV_KUNDE_UTEN_BRUK(Alvorlighet.ADVARSEL,
            "Kunden hadde aktiv avtale i kunderegisteret, men ingen bruk i perioden. Kontrollen kjøres ikke lenger.",
            true),
    /** The invoice's lines carry different fakturareferanse/bestillingsnummer, so LG04 splits it into several orders. */
    DELT_REFERANSE(Alvorlighet.ADVARSEL,
            "Linjene har ulik fakturareferanse eller bestillingsnummer, så kunden får én faktura i Unit4 per referanse."),
    /**
     * At least one line of the invoice has no fakturareferanse («Deres referanse») — Økonomi's kickoff wish
     * (specs/012). A warning until the mandatory-field list says otherwise (OQ-15).
     */
    MANGLER_FAKTURAREFERANSE(Alvorlighet.ADVARSEL,
            "Minst én linje på fakturaen har ingen fakturareferanse (Deres referanse). Se listen «Fakturaer uten "
                    + "fakturareferanse» nedenfor. Referansen rettes i CRM og kommer med neste import fra datavarehuset."),
    /** Customers whose monthly total is not over the price version's threshold get no invoice (specs/011, OQ-22). */
    UNDER_TERSKEL(Alvorlighet.ADVARSEL,
            "Kundens samlede beløp i perioden er ikke over terskelverdien i prisversjonen, så kunden faktureres ikke "
                    + "denne måneden. Se listen under Fakturaer.");

    private final Alvorlighet alvorlighet;
    private final String definisjon;
    private final boolean utgatt;

    Kontrollkode(Alvorlighet alvorlighet, String definisjon) {
        this(alvorlighet, definisjon, false);
    }

    Kontrollkode(Alvorlighet alvorlighet, String definisjon, boolean utgatt) {
        this.alvorlighet = alvorlighet;
        this.definisjon = definisjon;
        this.utgatt = utgatt;
    }

    /** No longer raised; kept so older runs' findings still render (specs/018). */
    public boolean utgatt() {
        return utgatt;
    }

    /** The codes generation can raise today, for lists that explain the controls. */
    public static java.util.List<Kontrollkode> aktive() {
        return java.util.Arrays.stream(values()).filter(k -> !k.utgatt).toList();
    }

    public Alvorlighet alvorlighet() {
        return alvorlighet;
    }

    public String definisjon() {
        return definisjon;
    }
}
