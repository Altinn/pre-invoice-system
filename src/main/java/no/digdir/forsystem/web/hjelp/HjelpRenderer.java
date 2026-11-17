package no.digdir.forsystem.web.hjelp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import no.digdir.forsystem.web.hjelp.Artikkel.Blokk;
import no.digdir.forsystem.web.hjelp.Artikkel.Del;
import no.digdir.forsystem.web.hjelp.Artikkel.Html;
import no.digdir.forsystem.web.hjelp.Artikkel.Overskrift;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.alerts.Alert;
import org.commonmark.ext.gfm.alerts.AlertTitle;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Heading;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

/**
 * Renders a learning-center article's Markdown to Designsystemet HTML (specs/016 research R1, R3, R5).
 *
 * <ul>
 *   <li>Raw HTML is escaped and unsafe URLs are dropped (FR-009).</li>
 *   <li>Headings, tables, lists, paragraphs and links get {@code ds-*} classes.</li>
 *   <li>GitHub alerts ({@code > [!TIP]}) become {@code div.ds-alert} with a Norwegian label.</li>
 *   <li>{@code [Generer](knapp:)} becomes {@code span.knapp}; a paragraph that is exactly {@code {{navn}}}
 *       becomes a {@link Blokk}.</li>
 *   <li>Relative {@code *.md} links are rewritten to {@code /hjelp/seksjon/side} and collected so the library
 *       can check them; absolute app paths are collected for {@code HjelpLenkerIT}; anything else is a problem.</li>
 * </ul>
 */
public final class HjelpRenderer {

    private static final Pattern BLOKK_MARKOR = Pattern.compile("<!--blokk:([^>]*?)-->");
    private static final Pattern BLOKK_ODELT = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern OVERSKRIFT = Pattern.compile("<h([23])\\b[^>]*?\\bid=\"([^\"]+)\"[^>]*>(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern TAGGER = Pattern.compile("<[^>]+>");
    private static final Pattern URL_SKJEMA = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /** A link from one article to another, after resolving the relative path. */
    public record ArtikkelLenke(String id, String anker) {}

    /**
     * Everything read from one article.
     *
     * @param frontMatter  raw front matter key → values
     * @param artikkelLenker  links to other articles
     * @param appLenker    absolute app paths used in the text
     * @param lokaleAnkre  {@code #anker} links within the same article
     * @param ankre        ids of all headings in this article
     * @param blokker      block names used
     * @param problemer    problems found while rendering (message only; the library adds the file name)
     */
    public record Resultat(
            Map<String, List<String>> frontMatter,
            List<Del> deler,
            List<Overskrift> innhold,
            List<ArtikkelLenke> artikkelLenker,
            List<String> appLenker,
            List<String> lokaleAnkre,
            Set<String> ankre,
            List<String> blokker,
            String tekst,
            List<String> problemer) {}

    /** Marks a block placeholder paragraph in the AST. */
    private static final class BlokkNode extends CustomBlock {
        final String navn;

        BlokkNode(String navn) {
            this.navn = navn;
        }
    }

    /** A button name written as {@code [Tekst](knapp:)}. */
    private static final class KnappNode extends CustomNode {}

    private final Parser parser;
    private final HtmlRenderer renderer;

    public HjelpRenderer() {
        List<Extension> utvidelser = List.of(
                TablesExtension.create(),
                YamlFrontMatterExtension.create(),
                AlertsExtension.create(),
                HeadingAnchorExtension.create());
        parser = Parser.builder().extensions(utvidelser).build();
        renderer = HtmlRenderer.builder()
                .escapeHtml(true)
                .sanitizeUrls(true)
                // Added before the extensions: the factory added first wins, so our alert markup replaces the
                // extension's default one.
                .nodeRendererFactory(KonvensjonsRenderer::new)
                .attributeProviderFactory(kontekst -> HjelpRenderer::attributter)
                .extensions(utvidelser)
                .build();
    }

    /**
     * @param markdown the whole file, including front matter
     * @param seksjon  the article's section folder, for resolving relative links
     * @param side     the article's file name without {@code .md}
     */
    public Resultat render(String markdown, String seksjon, String side) {
        Node dokument = parser.parse(markdown);
        Map<String, List<String>> frontMatter = new LinkedHashMap<>(YamlFrontMatterVisitor.readData(dokument));
        List<String> problemer = new ArrayList<>();
        List<ArtikkelLenke> artikkelLenker = new ArrayList<>();
        List<String> appLenker = new ArrayList<>();
        List<String> lokaleAnkre = new ArrayList<>();
        List<String> blokker = new ArrayList<>();

        omskriv(dokument, seksjon, side, problemer, artikkelLenker, appLenker, lokaleAnkre, blokker);

        String html = renderer.render(dokument)
                // A scrollable table or code block must be reachable by keyboard, and a table wrapped in a
                // scroll container keeps its table semantics (display: block on the table itself would drop them).
                .replace("<table ", "<div class=\"tabell-rull\" role=\"region\" tabindex=\"0\" aria-label=\"Tabell\"><table ")
                .replace("</table>", "</table></div>")
                .replace("<pre>", "<pre tabindex=\"0\">");
        List<Overskrift> innhold = new ArrayList<>();
        Set<String> ankre = new java.util.LinkedHashSet<>();
        Matcher m = OVERSKRIFT.matcher(html);
        while (m.find()) {
            ankre.add(m.group(2));
            if (m.group(1).equals("2")) {
                innhold.add(new Overskrift(m.group(2), avkode(TAGGER.matcher(m.group(3)).replaceAll(""))));
            }
        }
        return new Resultat(frontMatter, deler(html), List.copyOf(innhold), List.copyOf(artikkelLenker),
                List.copyOf(appLenker), List.copyOf(lokaleAnkre), ankre, List.copyOf(blokker), vanligTekst(dokument),
                problemer);
    }

    // ---- AST rewrite -------------------------------------------------------------------------------

    private void omskriv(Node rot, String seksjon, String side, List<String> problemer,
                         List<ArtikkelLenke> artikkelLenker, List<String> appLenker, List<String> lokaleAnkre,
                         List<String> blokker) {
        rot.accept(new AbstractVisitor() {
            @Override
            public void visit(Paragraph avsnitt) {
                if (avsnitt.getParent() instanceof org.commonmark.node.Document
                        && avsnitt.getFirstChild() instanceof Text t && avsnitt.getFirstChild() == avsnitt.getLastChild()) {
                    Matcher bm = BLOKK_ODELT.matcher(t.getLiteral().trim());
                    if (bm.matches()) {
                        String navn = bm.group(1).trim();
                        if (!navn.matches("[a-z0-9-]+")) {
                            problemer.add("ugyldig blokknavn «{{" + navn + "}}» (bare små bokstaver, tall og bindestrek)");
                            avsnitt.unlink();
                            return;
                        }
                        blokker.add(navn);
                        BlokkNode b = new BlokkNode(navn);
                        avsnitt.insertBefore(b);
                        avsnitt.unlink();
                        return;
                    }
                }
                visitChildren(avsnitt);
            }

            @Override
            public void visit(Heading overskrift) {
                if (overskrift.getLevel() != 2 && overskrift.getLevel() != 3) {
                    problemer.add("overskrift på nivå " + overskrift.getLevel() + " (bruk ## og ###; tittelen står i front matter)");
                }
                visitChildren(overskrift);
            }

            @Override
            public void visit(Image bilde) {
                problemer.add("bilder er ikke støttet: «" + bilde.getDestination() + "»");
            }

            @Override
            public void visit(Link lenke) {
                String mal = lenke.getDestination() == null ? "" : lenke.getDestination().trim();
                if (mal.equals("knapp:")) {
                    KnappNode k = new KnappNode();
                    Node barn = lenke.getFirstChild();
                    while (barn != null) {
                        Node neste = barn.getNext();
                        k.appendChild(barn);
                        barn = neste;
                    }
                    if (vanligTekst(k).isBlank()) {
                        problemer.add("knapp:-lenke uten tekst");
                    }
                    lenke.insertBefore(k);
                    lenke.unlink();
                    visitChildren(k);
                    return;
                }
                behandleLenke(lenke, mal);
                visitChildren(lenke);
            }

            private void behandleLenke(Link lenke, String mal) {
                if (mal.isEmpty()) {
                    problemer.add("lenke uten mål: «" + vanligTekst(lenke) + "»");
                } else if (mal.startsWith("#")) {
                    lokaleAnkre.add(mal.substring(1));
                } else if (mal.startsWith("/") && !mal.startsWith("//")) {
                    String sti = mal.replaceAll("[?#].*$", "");
                    appLenker.add(sti);
                } else if (URL_SKJEMA.matcher(mal).matches() || mal.startsWith("//")) {
                    problemer.add("ekstern lenke er ikke tillatt: «" + mal + "»");
                } else if (mal.endsWith(".md") || mal.contains(".md#")) {
                    int hash = mal.indexOf('#');
                    String sti = hash < 0 ? mal : mal.substring(0, hash);
                    String anker = hash < 0 ? null : mal.substring(hash + 1);
                    String id = oppslag(seksjon, sti);
                    if (id == null) {
                        problemer.add("lenken «" + mal + "» peker utenfor læringssenteret");
                    } else {
                        artikkelLenker.add(new ArtikkelLenke(id, anker));
                        lenke.setDestination("/hjelp/" + id + (anker == null ? "" : "#" + anker));
                    }
                } else {
                    problemer.add("lenken «" + mal + "» er verken en .md-lenke til en annen artikkel eller en sti i appen");
                }
            }
        });
    }

    /** Resolves {@code ../seksjon/side.md} against the current section; returns {@code seksjon/side} or null. */
    static String oppslag(String seksjon, String relativSti) {
        try {
            String sti = new URI(null, null, "/" + seksjon + "/x.md", null).resolve(new URI(null, null, relativSti, null)).getPath();
            String[] deler = sti.replaceFirst("^/", "").split("/");
            if (deler.length != 2 || !deler[1].endsWith(".md") || deler[0].isEmpty() || deler[0].equals("..")) {
                return null;
            }
            return deler[0] + "/" + deler[1].substring(0, deler[1].length() - 3);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    // ---- HTML splitting and text -----------------------------------------------------------------------

    private static List<Del> deler(String html) {
        List<Del> deler = new ArrayList<>();
        Matcher m = BLOKK_MARKOR.matcher(html);
        int fra = 0;
        while (m.find()) {
            if (m.start() > fra && !html.substring(fra, m.start()).isBlank()) {
                deler.add(new Html(html.substring(fra, m.start())));
            }
            deler.add(new Blokk(m.group(1)));
            fra = m.end();
        }
        if (fra < html.length() && !html.substring(fra).isBlank()) {
            deler.add(new Html(html.substring(fra)));
        }
        return List.copyOf(deler);
    }

    private static String avkode(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&").trim();
    }

    static String vanligTekst(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text t) {
                sb.append(t.getLiteral());
            }

            @Override
            public void visit(Code c) {
                sb.append(c.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak b) {
                sb.append(' ');
            }

            @Override
            public void visit(HardLineBreak b) {
                sb.append(' ');
            }

            @Override
            public void visit(Paragraph p) {
                visitChildren(p);
                sb.append(' ');
            }

            @Override
            public void visit(Heading h) {
                visitChildren(h);
                sb.append(' ');
            }

            @Override
            public void visit(org.commonmark.node.ListItem li) {
                visitChildren(li);
                sb.append(' ');
            }
        });
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    // ---- Attributes and node renderers -----------------------------------------------------------------

    private static void attributter(Node node, String tagName, Map<String, String> attr) {
        if (node instanceof Heading h) {
            attr.put("class", "ds-heading");
            attr.put("data-size", h.getLevel() <= 2 ? "sm" : "xs");
        } else if (node instanceof TableBlock && tagName.equals("table")) {
            attr.put("class", "ds-table");
            attr.put("data-size", "sm");
        } else if (node instanceof Link) {
            attr.put("class", "ds-link");
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            attr.put("class", "ds-list");
        } else if (node instanceof Paragraph && tagName.equals("p")) {
            attr.put("class", "ds-paragraph");
        }
    }

    /** Alerts, button names and block placeholders. */
    private static final class KonvensjonsRenderer implements NodeRenderer {
        private final HtmlNodeRendererContext ctx;
        private final HtmlWriter html;

        KonvensjonsRenderer(HtmlNodeRendererContext ctx) {
            this.ctx = ctx;
            this.html = ctx.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Alert.class, KnappNode.class, BlokkNode.class);
        }

        @Override
        public void render(Node node) {
            if (node instanceof Alert alert) {
                renderAlert(alert);
            } else if (node instanceof KnappNode k) {
                html.tag("span", Map.of("class", "knapp"));
                renderBarn(k);
                html.tag("/span");
            } else if (node instanceof BlokkNode b) {
                html.raw("<!--blokk:" + b.navn + "-->");
                html.line();
            }
        }

        private void renderAlert(Alert alert) {
            String type = alert.getType().toUpperCase(java.util.Locale.ROOT);
            String farge;
            String merkelapp;
            switch (type) {
                case "TIP", "NOTE" -> { farge = "info"; merkelapp = "Tips"; }
                case "IMPORTANT" -> { farge = "warning"; merkelapp = "Viktig"; }
                default -> { farge = "danger"; merkelapp = "Advarsel"; } // WARNING, CAUTION
            }
            Map<String, String> attr = new LinkedHashMap<>();
            attr.put("class", "ds-alert bred");
            attr.put("data-color", farge);
            html.line();
            html.tag("div", ctx.extendAttributes(alert, "div", attr));
            html.line();
            html.tag("p");
            html.tag("strong");
            html.text(merkelapp);
            html.tag("/strong");
            html.tag("/p");
            html.line();
            for (Node barn = alert.getFirstChild(); barn != null; barn = barn.getNext()) {
                if (!(barn instanceof AlertTitle)) {
                    ctx.render(barn);
                }
            }
            html.tag("/div");
            html.line();
        }

        private void renderBarn(Node forelder) {
            for (Node barn = forelder.getFirstChild(); barn != null; barn = barn.getNext()) {
                ctx.render(barn);
            }
        }
    }
}
