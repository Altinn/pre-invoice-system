package no.digdir.forsystem.web.hjelp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import no.digdir.forsystem.billing.Kontrollkode;
import no.digdir.forsystem.web.hjelp.Artikkel.Blokk;
import no.digdir.forsystem.web.hjelp.Artikkel.Del;
import no.digdir.forsystem.web.hjelp.Artikkel.Html;
import no.digdir.forsystem.web.hjelp.Artikkel.Lenke;
import no.digdir.forsystem.web.hjelp.Artikkel.Steg;
import no.digdir.forsystem.web.hjelp.HjelpRenderer.Resultat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The learning center's content, loaded once at startup (specs/016 research R2). Reads the Markdown articles,
 * {@code meny.yml} and {@code kontrollfunn.yml}, renders them, checks them against each other (data-model.md,
 * «Validation at load») and holds the result in memory. Every problem is collected into one
 * {@link IllegalStateException} naming each file, so a broken article stops startup — and therefore every
 * {@code @SpringBootTest} — before it can be merged.
 */
@Component
public class Hjelpebibliotek {

    static final Set<String> KJENTE_NOKLER = Set.of(
            "tittel", "sammendrag", "oppdatert", "rolle", "apne-i-appen", "apne-i-appen-tekst", "relatert", "nokkelord");
    static final Set<String> ROLLER = Set.of("LESER", "FORVALTER", "GODKJENNER");
    /** Live blocks the templates can render (hjelp/fragmenter.html, fragment {@code blokk}). */
    public static final Set<String> KJENTE_BLOKKER = Set.of("kontrollkoder", "livslop-kjoring", "livslop-prisversjon");

    private final Meny meny;
    private final Map<String, Artikkel> artikler;
    private final Map<Kontrollkode, String> kontrollfunn;
    private final Map<String, List<String>> appLenker;

    /** Loads {@code classpath:hjelp/}. */
    @Autowired
    public Hjelpebibliotek() {
        this(klassesti("hjelp"));
    }

    /** Loads a classpath folder, e.g. {@code "hjelp"}. */
    public Hjelpebibliotek(String klassestiRot) {
        this(klassesti(klassestiRot));
    }

    /** Loads a folder on disk (tests build small broken libraries this way). */
    public Hjelpebibliotek(Path katalog) {
        this(filer(katalog));
    }

    private Hjelpebibliotek(Kilde kilde) {
        List<String> problemer = new ArrayList<>();
        HjelpRenderer renderer = new HjelpRenderer();

        String menyTekst = kilde.filer().get("meny.yml");
        if (menyTekst == null) {
            problemer.add(kilde.navn() + "/meny.yml: filen mangler");
        }
        this.meny = menyTekst == null ? new Meny(List.of(), List.of())
                : Meny.les(menyTekst, kilde.navn() + "/meny.yml", problemer);

        Map<String, Artikkel> funnet = new TreeMap<>();
        Map<String, Resultat> resultater = new LinkedHashMap<>();
        Map<String, String> filnavn = new LinkedHashMap<>();
        for (var e : kilde.filer().entrySet()) {
            String sti = e.getKey();
            if (!sti.endsWith(".md")) {
                continue;
            }
            String[] biter = sti.split("/");
            String fil = kilde.navn() + "/" + sti;
            if (biter.length != 2) {
                problemer.add(fil + ": artikler skal ligge i hjelp/<seksjon>/<side>.md");
                continue;
            }
            String seksjon = biter[0];
            String side = biter[1].substring(0, biter[1].length() - 3);
            String id = seksjon + "/" + side;
            Resultat r = renderer.render(e.getValue(), seksjon, side);
            resultater.put(id, r);
            filnavn.put(id, fil);
            for (String p : r.problemer()) {
                problemer.add(fil + ": " + p);
            }
            for (String b : r.blokker()) {
                if (!KJENTE_BLOKKER.contains(b)) {
                    problemer.add(fil + ": ukjent blokk «{{" + b + "}}» (kjente: " + String.join(", ", KJENTE_BLOKKER.stream().sorted().toList()) + ")");
                }
            }
            Artikkel a = lagArtikkel(id, seksjon, side, r, fil, problemer);
            if (a != null) {
                funnet.put(id, a);
            }
        }

        sjekkMeny(kilde.navn(), funnet.keySet(), problemer);
        sjekkLenker(funnet, resultater, filnavn, problemer);

        this.artikler = byggSteg(funnet);
        this.kontrollfunn = lesKontrollfunn(kilde, renderer, funnet, problemer);
        Map<String, List<String>> app = new LinkedHashMap<>();
        resultater.forEach((id, r) -> {
            Set<String> sti = new LinkedHashSet<>(r.appLenker());
            Artikkel a = funnet.get(id);
            if (a != null && a.apneIAppen() != null) {
                sti.add(a.apneIAppen());
            }
            if (!sti.isEmpty()) {
                app.put(id, List.copyOf(sti));
            }
        });
        kontrollfunnApp.forEach((kode, stier) -> app.put("kontrollfunn.yml/" + kode, List.copyOf(stier)));
        this.appLenker = Map.copyOf(app);

        for (Meny.JegVil j : meny.jegVil()) {
            if (!funnet.containsKey(j.artikkelId())) {
                problemer.add(kilde.navn() + "/meny.yml: «jeg-vil» peker på en artikkel som ikke finnes: " + j.artikkelId());
            }
        }

        if (!problemer.isEmpty()) {
            throw new IllegalStateException("Læringssenteret har " + problemer.size() + " feil i innholdet:\n - "
                    + String.join("\n - ", problemer));
        }
    }

    private final Map<String, List<String>> kontrollfunnApp = new LinkedHashMap<>();

    // ---- Public API ------------------------------------------------------------------------------------

    public Optional<Artikkel> artikkel(String seksjon, String side) {
        return Optional.ofNullable(artikler.get(seksjon + "/" + side));
    }

    public Optional<Artikkel> artikkel(String id) {
        return Optional.ofNullable(artikler.get(id));
    }

    public Meny meny() {
        return meny;
    }

    /** The start page's «Jeg vil …» list. */
    public List<Meny.JegVil> jegVil() {
        return meny.jegVil();
    }

    /** «Slik løser du det» as HTML, per control code. */
    public Map<Kontrollkode, String> kontrollfunn() {
        return kontrollfunn;
    }

    public List<Artikkel> alle() {
        return List.copyOf(artikler.values());
    }

    /** Article id (or {@code kontrollfunn.yml/KODE}) → app paths it links to, for {@code HjelpLenkerIT}. */
    public Map<String, List<String>> appLenker() {
        return appLenker;
    }

    /** First article of a section in menu order, or empty. */
    public Optional<Artikkel> forsteI(String seksjonId) {
        return meny.seksjoner().stream().filter(s -> s.id().equals(seksjonId)).findFirst()
                .flatMap(s -> s.artikkelIder().stream().findFirst()).flatMap(this::artikkel);
    }

    // ---- Building and validation -----------------------------------------------------------------------

    private Artikkel lagArtikkel(String id, String seksjon, String side, Resultat r, String fil, List<String> problemer) {
        Map<String, List<String>> fm = r.frontMatter();
        boolean ok = true;
        for (String nokkel : fm.keySet()) {
            if (!KJENTE_NOKLER.contains(nokkel)) {
                problemer.add(fil + ": ukjent front matter-nøkkel «" + nokkel + "»");
                ok = false;
            }
        }
        String tittel = enkel(fm, "tittel", fil, problemer);
        if (tittel == null) {
            problemer.add(fil + ": «tittel» mangler");
            ok = false;
        } else if (tittel.length() > 80) {
            problemer.add(fil + ": «tittel» er lenger enn 80 tegn");
            ok = false;
        }
        String sammendrag = enkel(fm, "sammendrag", fil, problemer);
        if (sammendrag == null) {
            problemer.add(fil + ": «sammendrag» mangler");
            ok = false;
        } else if (sammendrag.length() > 200) {
            problemer.add(fil + ": «sammendrag» er lenger enn 200 tegn");
            ok = false;
        }
        LocalDate oppdatert = null;
        String oppdatertTekst = enkel(fm, "oppdatert", fil, problemer);
        if (oppdatertTekst == null) {
            problemer.add(fil + ": «oppdatert» mangler");
            ok = false;
        } else {
            try {
                oppdatert = LocalDate.parse(oppdatertTekst);
                if (oppdatert.isAfter(LocalDate.now())) {
                    problemer.add(fil + ": «oppdatert» (" + oppdatertTekst + ") ligger frem i tid");
                    ok = false;
                }
            } catch (DateTimeException e) {
                problemer.add(fil + ": «oppdatert» må være en ISO-dato (åååå-mm-dd), fikk «" + oppdatertTekst + "»");
                ok = false;
            }
        }
        String rolle = enkel(fm, "rolle", fil, problemer);
        if (rolle != null && !ROLLER.contains(rolle)) {
            problemer.add(fil + ": «rolle» må være LESER, FORVALTER eller GODKJENNER, fikk «" + rolle + "»");
            ok = false;
        }
        String apne = enkel(fm, "apne-i-appen", fil, problemer);
        if (apne != null && (!apne.startsWith("/") || apne.startsWith("//"))) {
            problemer.add(fil + ": «apne-i-appen» må være en absolutt sti i appen, fikk «" + apne + "»");
            ok = false;
        }
        String apneTekst = enkel(fm, "apne-i-appen-tekst", fil, problemer);
        if (apne != null && apneTekst == null) {
            apneTekst = standardKnappetekst(apne);
        }
        List<String> relatert = liste(fm.get("relatert"));
        List<String> nokkelord = liste(fm.get("nokkelord"));
        if (!ok) {
            return null;
        }
        return new Artikkel(id, seksjon, side, tittel, sammendrag, rolle, apne, apneTekst, List.copyOf(relatert),
                oppdatert, List.copyOf(nokkelord), r.deler(), r.innhold(), r.tekst(), null);
    }

    /** A list value: block style ({@code - a}) comes ready; flow style ({@code [a, b]}) arrives as one string. */
    static List<String> liste(List<String> verdier) {
        List<String> ut = new ArrayList<>();
        for (String v : verdier == null ? List.<String>of() : verdier) {
            String t = v == null ? "" : v.trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                t = t.substring(1, t.length() - 1);
                for (String del : t.split(",")) {
                    String d = del.trim().replaceAll("^['\"]|['\"]$", "");
                    if (!d.isBlank()) {
                        ut.add(d);
                    }
                }
            } else if (!t.isBlank()) {
                ut.add(t);
            }
        }
        return ut;
    }

    private static String enkel(Map<String, List<String>> fm, String nokkel, String fil, List<String> problemer) {
        List<String> v = fm.get(nokkel);
        if (v == null || v.isEmpty()) {
            return null;
        }
        if (v.size() > 1) {
            problemer.add(fil + ": «" + nokkel + "» skal ha én verdi");
        }
        String s = v.get(0);
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String standardKnappetekst(String sti) {
        if (sti.equals("/")) {
            return "Åpne forsiden";
        }
        String siste = sti.substring(sti.lastIndexOf('/') + 1);
        String navn = switch (siste) {
            case "kjoringer" -> "Kjøringer";
            case "hendelseslogg" -> "Hendelseslogg";
            case "" -> "forsiden";
            default -> Character.toUpperCase(siste.charAt(0)) + siste.substring(1);
        };
        return "Åpne " + navn;
    }

    private void sjekkMeny(String navn, Set<String> artikkelIder, List<String> problemer) {
        Map<String, Integer> antall = new LinkedHashMap<>();
        for (Meny.Seksjon s : meny.seksjoner()) {
            for (String id : s.artikkelIder()) {
                antall.merge(id, 1, Integer::sum);
                if (!id.startsWith(s.id() + "/")) {
                    problemer.add(navn + "/meny.yml: «" + id + "» ligger ikke i mappen til seksjonen «" + s.id() + "»");
                }
            }
        }
        antall.forEach((id, n) -> {
            if (n > 1) {
                problemer.add(navn + "/meny.yml: «" + id + "» står " + n + " ganger i menyen");
            }
            if (!artikkelIder.contains(id)) {
                problemer.add(navn + "/meny.yml: menyen peker på «" + id + "», men filen " + id + ".md finnes ikke");
            }
        });
        for (String id : artikkelIder) {
            if (!antall.containsKey(id)) {
                problemer.add(navn + "/" + id + ".md: artikkelen står ikke i meny.yml");
            }
        }
    }

    private void sjekkLenker(Map<String, Artikkel> artikler, Map<String, Resultat> resultater,
                             Map<String, String> filnavn, List<String> problemer) {
        resultater.forEach((id, r) -> {
            String fil = filnavn.get(id);
            for (String anker : r.lokaleAnkre()) {
                if (!r.ankre().contains(anker)) {
                    problemer.add(fil + ": lenken «#" + anker + "» peker på en overskrift som ikke finnes i artikkelen");
                }
            }
            for (var l : r.artikkelLenker()) {
                sjekkMaal(l.id(), l.anker(), resultater, fil, problemer);
            }
            Artikkel a = artikler.get(id);
            if (a != null) {
                for (String rel : a.relatert()) {
                    if (!resultater.containsKey(rel)) {
                        problemer.add(fil + ": «relatert» peker på en artikkel som ikke finnes: " + rel);
                    }
                }
            }
        });
    }

    private static void sjekkMaal(String id, String anker, Map<String, Resultat> resultater, String fil,
                                  List<String> problemer) {
        Resultat maal = resultater.get(id);
        if (maal == null) {
            problemer.add(fil + ": lenken til «" + id + ".md» peker på en artikkel som ikke finnes");
        } else if (anker != null && !maal.ankre().contains(anker)) {
            problemer.add(fil + ": lenken til «" + id + ".md#" + anker + "» peker på en overskrift som ikke finnes");
        }
    }

    private Map<String, Artikkel> byggSteg(Map<String, Artikkel> funnet) {
        Map<String, Artikkel> ut = new TreeMap<>(funnet);
        for (Meny.Seksjon s : meny.seksjoner()) {
            if (!s.sti()) {
                continue;
            }
            List<Artikkel> rekke = s.artikkelIder().stream().map(funnet::get).filter(java.util.Objects::nonNull).toList();
            List<Lenke> lenker = rekke.stream().map(a -> new Lenke(a.id(), a.url(), a.tittel())).toList();
            for (int i = 0; i < rekke.size(); i++) {
                Artikkel a = rekke.get(i);
                ut.put(a.id(), a.medSteg(new Steg(i + 1, rekke.size(),
                        i > 0 ? lenker.get(i - 1) : null, i < rekke.size() - 1 ? lenker.get(i + 1) : null, lenker)));
            }
        }
        return Map.copyOf(ut);
    }

    @SuppressWarnings("unchecked")
    private Map<Kontrollkode, String> lesKontrollfunn(Kilde kilde, HjelpRenderer renderer, Map<String, Artikkel> funnet,
                                                      List<String> problemer) {
        String fil = kilde.navn() + "/kontrollfunn.yml";
        String yaml = kilde.filer().get("kontrollfunn.yml");
        if (yaml == null) {
            problemer.add(fil + ": filen mangler");
            return Map.of();
        }
        Object rot;
        try {
            rot = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (RuntimeException e) {
            problemer.add(fil + ": ugyldig YAML: " + e.getMessage());
            return Map.of();
        }
        if (!(rot instanceof Map<?, ?> kart)) {
            problemer.add(fil + ": forventet en liste «KODE: tekst»");
            return Map.of();
        }
        Map<Kontrollkode, String> ut = new java.util.EnumMap<>(Kontrollkode.class);
        Set<String> sett = new LinkedHashSet<>();
        for (var e : kart.entrySet()) {
            String kode = String.valueOf(e.getKey());
            sett.add(kode);
            Kontrollkode k;
            try {
                k = Kontrollkode.valueOf(kode);
            } catch (IllegalArgumentException ex) {
                problemer.add(fil + ": «" + kode + "» er ikke en kontrollkode (ukjent nøkkel)");
                continue;
            }
            String tekst = e.getValue() == null ? "" : e.getValue().toString();
            if (tekst.isBlank()) {
                problemer.add(fil + ": «" + kode + "» har ingen «Slik løser du det»-tekst");
                continue;
            }
            Resultat r = renderer.render(tekst, "oppslag", "kontrollfunn");
            r.problemer().forEach(p -> problemer.add(fil + " (" + kode + "): " + p));
            for (var l : r.artikkelLenker()) {
                if (!funnet.containsKey(l.id())) {
                    problemer.add(fil + " (" + kode + "): lenken til «" + l.id() + ".md» peker på en artikkel som ikke finnes");
                }
            }
            if (!r.appLenker().isEmpty()) {
                kontrollfunnApp.put(kode, r.appLenker());
            }
            if (!r.blokker().isEmpty()) {
                problemer.add(fil + " (" + kode + "): blokker kan ikke brukes her");
            }
            StringBuilder html = new StringBuilder();
            for (Del d : r.deler()) {
                if (d instanceof Html h) {
                    html.append(h.html());
                }
            }
            ut.put(k, html.toString());
        }
        for (Kontrollkode k : EnumSet.allOf(Kontrollkode.class)) {
            if (!sett.contains(k.name())) {
                problemer.add(fil + ": kontrollkoden " + k.name() + " mangler «Slik løser du det»-tekst");
            }
        }
        return Map.copyOf(ut);
    }

    // ---- Reading sources -------------------------------------------------------------------------------

    private record Kilde(String navn, Map<String, String> filer) {}

    private static Kilde klassesti(String rot) {
        Map<String, String> filer = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            String markor = "/" + rot + "/";
            for (Resource r : resolver.getResources("classpath:" + rot + "/**")) {
                if (!r.isReadable() || r.getFilename() == null) {
                    continue;
                }
                String url = r.getURL().toString();
                int i = url.lastIndexOf(markor);
                if (i < 0 || url.endsWith("/")) {
                    continue;
                }
                String sti = url.substring(i + markor.length());
                if (sti.endsWith(".md") || sti.equals("meny.yml") || sti.equals("kontrollfunn.yml")) {
                    filer.put(sti, new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (java.io.FileNotFoundException e) {
            // No such folder: loading reports the missing meny.yml and kontrollfunn.yml.
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese " + rot, e);
        }
        return new Kilde(rot, filer);
    }

    private static Kilde filer(Path katalog) {
        Map<String, String> filer = new TreeMap<>();
        try (Stream<Path> s = Files.walk(katalog)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String sti = katalog.relativize(p).toString().replace('\\', '/');
                if (sti.endsWith(".md") || sti.equals("meny.yml") || sti.equals("kontrollfunn.yml")) {
                    filer.put(sti, Files.readString(p));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese " + katalog, e);
        }
        return new Kilde(katalog.getFileName().toString(), filer);
    }
}
