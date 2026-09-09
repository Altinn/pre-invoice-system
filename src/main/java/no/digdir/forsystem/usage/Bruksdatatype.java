package no.digdir.forsystem.usage;

/**
 * Kind of usage row (docs/03 V3). BRUKSVOLUM carries {@code antall} (volume × price later);
 * the cost types carry {@code belop} as pass-through. SMS/Azure specifics are provisional (OQ-3).
 */
public enum Bruksdatatype {
    BRUKSVOLUM,
    AZURE_KOSTNAD,
    SMS_KOSTNAD;

    public boolean erVolum() {
        return this == BRUKSVOLUM;
    }
}
