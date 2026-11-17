package no.digdir.forsystem.web.hjelp;

import java.time.LocalDate;
import java.util.List;

/**
 * One learning-center article (specs/016 data-model.md): front matter plus the rendered body. Immutable and
 * held in memory by {@link Hjelpebibliotek}.
 *
 * @param id       {@code seksjon/side}, from the file path
 * @param deler    the body as ordered HTML parts and block placeholders
 * @param innhold  the h2 headings, for «På denne siden»
 * @param tekst    plain text, for search
 * @param steg     position in an ordered path, or {@code null} outside a {@code sti} section
 */
public record Artikkel(
        String id,
        String seksjon,
        String side,
        String tittel,
        String sammendrag,
        String rolle,
        String apneIAppen,
        String apneIAppenTekst,
        List<String> relatert,
        LocalDate oppdatert,
        List<String> nokkelord,
        List<Del> deler,
        List<Overskrift> innhold,
        String tekst,
        Steg steg) {

    public String url() {
        return "/hjelp/" + id;
    }

    /** A body part: ready HTML, or the name of a live block the template renders (e.g. {@code kontrollkoder}). */
    public sealed interface Del permits Html, Blokk {
        default boolean erBlokk() {
            return this instanceof Blokk;
        }
    }

    public record Html(String html) implements Del {}

    public record Blokk(String navn) implements Del {}

    /** An h2 heading: the anchor id and the plain heading text. */
    public record Overskrift(String anker, String tekst) {}

    /** A link to another article. */
    public record Lenke(String id, String url, String tittel) {}

    /** Position {@code nr} of {@code av} in an ordered path, with neighbours and the full list for the stepper. */
    public record Steg(int nr, int av, Lenke forrige, Lenke neste, List<Lenke> liste) {}

    public Artikkel medSteg(Steg nyttSteg) {
        return new Artikkel(id, seksjon, side, tittel, sammendrag, rolle, apneIAppen, apneIAppenTekst, relatert,
                oppdatert, nokkelord, deler, innhold, tekst, nyttSteg);
    }
}
