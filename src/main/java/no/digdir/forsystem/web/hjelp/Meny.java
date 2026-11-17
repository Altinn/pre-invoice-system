package no.digdir.forsystem.web.hjelp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The learning-center menu, read from {@code meny.yml} (specs/016 data-model.md): sections in file order,
 * optional groups, and the start page's «Jeg vil …» list.
 */
public record Meny(List<Seksjon> seksjoner, List<JegVil> jegVil) {

    /** Fixed order of the sections (FR-004); a section with no articles is not shown. */
    public static final List<String> REKKEFOLGE =
            List.of("kom-i-gang", "manedsrutinen", "slik-gjor-du", "nar-noe-gar-galt", "oppslag", "forsta-systemet");

    /** @param sti an ordered path: «Steg n av N», stepper and Forrige/Neste */
    public record Seksjon(String id, String tittel, boolean sti, List<Gruppe> grupper) {
        public List<String> artikkelIder() {
            return grupper.stream().flatMap(g -> g.artikkelIder().stream()).toList();
        }

        public boolean harArtikler() {
            return !artikkelIder().isEmpty();
        }

        public boolean harGrupper() {
            return grupper.stream().anyMatch(g -> g.tittel() != null);
        }
    }

    /** @param tittel subheading in the menu, or {@code null} for an ungrouped list; ids are {@code seksjon/side} */
    public record Gruppe(String tittel, List<String> artikkelIder) {}

    public record JegVil(String tekst, String artikkelId) {}

    /** Sections that have articles, in FR-004 order. */
    public List<Seksjon> synligeSeksjoner() {
        return seksjoner.stream()
                .filter(Seksjon::harArtikler)
                .sorted(java.util.Comparator.comparingInt(s -> {
                    int i = REKKEFOLGE.indexOf(s.id());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }))
                .toList();
    }

    /** Parses {@code meny.yml}; structural problems are added to {@code problemer} rather than thrown. */
    @SuppressWarnings("unchecked")
    static Meny les(String yaml, String fil, List<String> problemer) {
        Object rot;
        try {
            rot = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (RuntimeException e) {
            problemer.add(fil + ": ugyldig YAML: " + e.getMessage());
            return new Meny(List.of(), List.of());
        }
        if (!(rot instanceof Map<?, ?> kart)) {
            problemer.add(fil + ": forventet «seksjoner» og «jeg-vil» på øverste nivå");
            return new Meny(List.of(), List.of());
        }
        for (Object nokkel : kart.keySet()) {
            if (!"seksjoner".equals(nokkel) && !"jeg-vil".equals(nokkel)) {
                problemer.add(fil + ": ukjent nøkkel «" + nokkel + "»");
            }
        }
        List<Seksjon> seksjoner = new ArrayList<>();
        if (kart.get("seksjoner") instanceof List<?> liste) {
            for (Object o : liste) {
                if (!(o instanceof Map<?, ?> s)) {
                    problemer.add(fil + ": en seksjon må være en nøkkel/verdi-liste");
                    continue;
                }
                for (Object nokkel : s.keySet()) {
                    if (!List.of("id", "tittel", "sti", "artikler", "grupper").contains(nokkel)) {
                        problemer.add(fil + ": ukjent nøkkel «" + nokkel + "» i en seksjon");
                    }
                }
                String id = tekst(s.get("id"));
                String tittel = tekst(s.get("tittel"));
                if (id == null || tittel == null) {
                    problemer.add(fil + ": en seksjon mangler «id» eller «tittel»");
                    continue;
                }
                List<Gruppe> grupper = new ArrayList<>();
                if (s.get("artikler") instanceof List<?> artikler) {
                    grupper.add(new Gruppe(null, ider(id, artikler)));
                }
                if (s.get("grupper") instanceof List<?> gl) {
                    for (Object g : gl) {
                        if (g instanceof Map<?, ?> gm && gm.get("artikler") instanceof List<?> artikler) {
                            grupper.add(new Gruppe(tekst(gm.get("tittel")), ider(id, artikler)));
                        } else {
                            problemer.add(fil + ": en gruppe i «" + id + "» må ha «artikler»");
                        }
                    }
                }
                seksjoner.add(new Seksjon(id, tittel, Boolean.TRUE.equals(s.get("sti")), List.copyOf(grupper)));
            }
        }
        List<JegVil> jegVil = new ArrayList<>();
        if (kart.get("jeg-vil") instanceof List<?> liste) {
            for (Object o : liste) {
                if (o instanceof Map<?, ?> m && tekst(m.get("tekst")) != null && tekst(m.get("artikkel")) != null) {
                    jegVil.add(new JegVil(tekst(m.get("tekst")), tekst(m.get("artikkel"))));
                } else {
                    problemer.add(fil + ": en «jeg-vil»-linje må ha «tekst» og «artikkel»");
                }
            }
        }
        return new Meny(List.copyOf(seksjoner), List.copyOf(jegVil));
    }

    private static List<String> ider(String seksjon, List<?> sider) {
        return sider.stream().map(s -> seksjon + "/" + s).toList();
    }

    private static String tekst(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }
}
