package no.digdir.forsystem.usage;

/** Natural key of one normalized monthly usage value. */
public record BruksdataNokkel(
        String organisasjonsnummer,
        Long produktId,
        String type) implements Comparable<BruksdataNokkel> {

    public static BruksdataNokkel fra(Bruksdata rad) {
        return new BruksdataNokkel(rad.organisasjonsnummer(), rad.produktId(), rad.type());
    }

    @Override
    public int compareTo(BruksdataNokkel annen) {
        int org = organisasjonsnummer.compareTo(annen.organisasjonsnummer);
        if (org != 0) {
            return org;
        }
        int produkt = produktId.compareTo(annen.produktId);
        return produkt != 0 ? produkt : type.compareTo(annen.type);
    }
}
