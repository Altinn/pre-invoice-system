package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.stream.Collectors;
import no.digdir.forsystem.billing.Kontrollkode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every validation rule in specs/016 data-model.md fails loading with a message naming the file. */
class HjelpebibliotekTest {

    @TempDir Path katalog;

    private static final String GYLDIG = """
            ---
            tittel: En artikkel
            sammendrag: Én setning.
            oppdatert: 2026-11-01
            ---
            ## Del en

            Tekst.
            """;

    /** A small valid library; each test changes one thing. */
    private void skriv(String sti, String innhold) throws IOException {
        Path p = katalog.resolve(sti);
        Files.createDirectories(p.getParent());
        Files.writeString(p, innhold);
    }

    private void gyldigBibliotek() throws IOException {
        skriv("a/en.md", GYLDIG);
        skriv("a/to.md", GYLDIG.replace("En artikkel", "To"));
        skriv("meny.yml", """
                seksjoner:
                  - id: a
                    tittel: A
                    artikler: [en, to]
                jeg-vil:
                  - tekst: gjøre noe
                    artikkel: a/en
                """);
        skriv("kontrollfunn.yml", EnumSet.allOf(Kontrollkode.class).stream()
                .map(k -> k.name() + ": Rett det.").collect(Collectors.joining("\n")));
    }

    private void forventFeil(String... biter) {
        assertThatThrownBy(() -> new Hjelpebibliotek(katalog)).isInstanceOf(IllegalStateException.class)
                .satisfies(e -> {
                    for (String b : biter) {
                        assertThat(e.getMessage()).contains(b);
                    }
                });
    }

    @Test
    void gyldigBibliotekLaster() throws IOException {
        gyldigBibliotek();
        Hjelpebibliotek b = new Hjelpebibliotek(katalog);
        assertThat(b.alle()).hasSize(2);
        assertThat(b.artikkel("a", "en")).isPresent();
        assertThat(b.kontrollfunn()).hasSize(Kontrollkode.values().length);
    }

    @Test
    void manglendeTittel() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG.replace("tittel: En artikkel\n", ""));
        forventFeil("a/en.md", "«tittel» mangler");
    }

    @Test
    void ukjentFrontMatterNokkel() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG.replace("---\n##", "titel: feil\n---\n##"));
        forventFeil("a/en.md", "ukjent front matter-nøkkel «titel»");
    }

    @Test
    void oppdatertIFremtiden() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG.replace("2026-11-01", "2999-01-01"));
        forventFeil("a/en.md", "frem i tid");
    }

    @Test
    void artikkelUtenMenyoppforing() throws IOException {
        gyldigBibliotek();
        skriv("a/tre.md", GYLDIG);
        forventFeil("a/tre.md", "står ikke i meny.yml");
    }

    @Test
    void menyoppforingUtenFil() throws IOException {
        gyldigBibliotek();
        Files.delete(katalog.resolve("a/to.md"));
        forventFeil("meny.yml", "a/to", "finnes ikke");
    }

    @Test
    void artikkelListetToGanger() throws IOException {
        gyldigBibliotek();
        skriv("meny.yml", Files.readString(katalog.resolve("meny.yml")).replace("[en, to]", "[en, to, en]"));
        forventFeil("meny.yml", "a/en", "2 ganger");
    }

    @Test
    void odelagtMdLenke() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\nSe [x](finnes-ikke.md).\n");
        forventFeil("a/en.md", "a/finnes-ikke.md", "ikke finnes");
    }

    @Test
    void odelagtAnker() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\nSe [x](to.md#ingen-slik).\n");
        forventFeil("a/en.md", "to.md#ingen-slik");
    }

    @Test
    void lenkeTilGyldigAnkerGaar() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\nSe [x](to.md#del-en) og [y](#del-en).\n");
        new Hjelpebibliotek(katalog);
    }

    @Test
    void eksternLenke() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\nSe [x](https://example.com).\n");
        forventFeil("a/en.md", "ekstern lenke");
    }

    @Test
    void ukjentBlokk() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\n{{finnes-ikke}}\n");
        forventFeil("a/en.md", "ukjent blokk", "finnes-ikke");
    }

    @Test
    void tomKnappetekst() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG + "\nTrykk [](knapp:).\n");
        forventFeil("a/en.md", "knapp:");
    }

    @Test
    void kontrollfunnManglerEnKode() throws IOException {
        gyldigBibliotek();
        skriv("kontrollfunn.yml", Files.readString(katalog.resolve("kontrollfunn.yml"))
                .replace("MANGLER_PRIS: Rett det.", ""));
        forventFeil("kontrollfunn.yml", "MANGLER_PRIS");
    }

    @Test
    void kontrollfunnHarUkjentNokkel() throws IOException {
        gyldigBibliotek();
        skriv("kontrollfunn.yml", Files.readString(katalog.resolve("kontrollfunn.yml")) + "\nIKKE_EN_KODE: x\n");
        forventFeil("kontrollfunn.yml", "IKKE_EN_KODE");
    }

    @Test
    void jegVilPekerUtIntet() throws IOException {
        gyldigBibliotek();
        skriv("meny.yml", Files.readString(katalog.resolve("meny.yml")).replace("artikkel: a/en", "artikkel: a/nei"));
        forventFeil("meny.yml", "jeg-vil", "a/nei");
    }

    @Test
    void relatertPekerUtIntet() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG.replace("---\n##", "relatert: [a/nei]\n---\n##"));
        forventFeil("a/en.md", "relatert", "a/nei");
    }

    @Test
    void alleProblemerRapporteresSamlet() throws IOException {
        gyldigBibliotek();
        skriv("a/en.md", GYLDIG.replace("tittel: En artikkel\n", ""));
        skriv("a/to.md", GYLDIG + "\n[x](https://example.com)\n");
        forventFeil("a/en.md", "a/to.md");
    }

    @Test
    void stegBygges() throws IOException {
        gyldigBibliotek();
        skriv("meny.yml", Files.readString(katalog.resolve("meny.yml")).replace("tittel: A\n", "tittel: A\n    sti: true\n"));
        Hjelpebibliotek b = new Hjelpebibliotek(katalog);
        var steg = b.artikkel("a", "to").orElseThrow().steg();
        assertThat(steg.nr()).isEqualTo(2);
        assertThat(steg.av()).isEqualTo(2);
        assertThat(steg.forrige().url()).isEqualTo("/hjelp/a/en");
        assertThat(steg.neste()).isNull();
    }

    /** The real content shipped in the jar must load, and cover every control code (FR-012, FR-017). */
    @Test
    void ekteInnholdLasterRent() {
        Hjelpebibliotek b = new Hjelpebibliotek();
        assertThat(b.alle()).isNotEmpty();
        assertThat(b.kontrollfunn().keySet()).isEqualTo(EnumSet.allOf(Kontrollkode.class));
        assertThat(b.kontrollfunn().values()).allSatisfy(t -> assertThat(t).isNotBlank());
        assertThat(b.jegVil().size()).isBetween(10, 15);
    }
}
