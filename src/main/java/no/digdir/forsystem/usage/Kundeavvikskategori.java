package no.digdir.forsystem.usage;

/**
 * What changed for one customer between what was invoiced and the new candidate (specs/019 FR-004..FR-006).
 * The first three come from the product changes; the rest compare the customer values themselves and can
 * have a transaction difference of 0.
 */
public enum Kundeavvikskategori {
    LAGT_TIL("Lagt til", false),
    ENDRET("Endret", false),
    FJERNET("Fjernet", false),
    ENDRET_FAKTURAREFERANSE("Endret fakturareferanse", true),
    ENDRET_BESTILLINGSNUMMER("Endret bestillingsnummer", true),
    ENDRET_KUNDENUMMER("Endret kundenummer", true),
    // The name is not in the LG04 file (Kundedata), so it is shown but does not stop a delta correction.
    ENDRET_NAVN("Endret navn", false);

    private final String tekst;
    private final boolean hindrerDifferanse;

    Kundeavvikskategori(String tekst, boolean hindrerDifferanse) {
        this.tekst = tekst;
        this.hindrerDifferanse = hindrerDifferanse;
    }

    public String tekst() {
        return tekst;
    }

    /**
     * Whether a delta correction cannot express this change (FR-011): a delta invoice would carry the new value
     * while the invoice it corrects carried the old one. Awaits the credit mechanism (OQ-6, OQ-29).
     */
    public boolean hindrerDifferanse() {
        return hindrerDifferanse;
    }

    static Kundeavvikskategori fra(Endringstype endringstype) {
        return switch (endringstype) {
            case TILAGT -> LAGT_TIL;
            case ENDRET -> ENDRET;
            case FJERNET -> FJERNET;
        };
    }
}
