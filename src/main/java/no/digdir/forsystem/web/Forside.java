package no.digdir.forsystem.web;

import java.util.List;

/**
 * Everything the start page shows (specs/015). Built by {@link ForsideService}; plain values only, so the
 * template formats nothing but numbers.
 *
 * @param hilsen       «God morgen, Adi» — without a name when none is known
 * @param dag          today, «onsdag 11. november 2026»
 * @param periode      the dashboard period as «januar 2027», or {@code null} when there is no data yet
 * @param status       one sentence on where invoicing stands
 * @param handling     the one primary action
 * @param fremdrift    the progress strip, or {@code null} without a period
 * @param nokkeltall   the key-number tiles (empty without a period)
 * @param produkter    the per-product split, or {@code null} when there is nothing to split
 * @param oppmerksomhet things that need someone, most severe first; empty means «Alt i rute»
 * @param aktivitet    the newest audit rows as sentences
 */
public record Forside(
        String hilsen,
        String dag,
        String periode,
        String status,
        Lenke handling,
        Fremdrift fremdrift,
        List<Nokkeltall> nokkeltall,
        Produktfordeling produkter,
        List<Varsel> oppmerksomhet,
        List<Aktivitet> aktivitet) {

    public record Lenke(String tekst, String href) {
    }

    /**
     * One tile.
     *
     * @param verdi    the big value, already formatted
     * @param detaljer smaller lines under it; may be empty
     */
    public record Nokkeltall(String etikett, String verdi, List<String> detaljer) {
    }

    /**
     * Bars per product.
     *
     * @param tittel «Fakturagrunnlag per produkt» or «Bruksvolum per produkt»
     * @param enhet  «kr» or «transaksjoner», for the screen-reader text
     */
    public record Produktfordeling(String tittel, String enhet, List<Stolpe> stolper) {
    }

    /** @param prosent share of the largest bar, 0–100 */
    public record Stolpe(String navn, String verdi, int prosent) {
    }

    /** @param niva {@code feil}, {@code advarsel} or {@code info} — the checklist classes in forsystem.css */
    public record Varsel(String niva, String tekst, String href) {
    }

    public record Aktivitet(String tekst, String bruker, String naar, String tidspunkt) {
    }
}
