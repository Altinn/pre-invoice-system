package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** «Hjelp til denne siden» must never point at an article that does not exist (specs/016 research R10). */
class HjelpFragmentTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern HJELP_KALL = Pattern.compile("layout :: hjelp\\(([^\\n]*)");
    private static final Pattern ARTIKKEL_ID = Pattern.compile("'([a-z0-9-]+/[a-z0-9-]+)'");

    @Test
    void hverHjelpelenkeIMalenePekerPaaEnArtikkel() throws IOException {
        Hjelpebibliotek bibliotek = new Hjelpebibliotek();
        List<String> feil = new ArrayList<>();
        int funnet = 0;
        try (Stream<Path> filer = Files.walk(TEMPLATES)) {
            for (Path mal : filer.filter(p -> p.toString().endsWith(".html")).sorted().toList()) {
                Matcher kall = HJELP_KALL.matcher(Files.readString(mal));
                while (kall.find()) {
                    Matcher id = ARTIKKEL_ID.matcher(kall.group(1));
                    while (id.find()) {
                        funnet++;
                        if (bibliotek.artikkel(id.group(1)).isEmpty()) {
                            feil.add(mal + " → " + id.group(1));
                        }
                    }
                }
            }
        }
        assertThat(funnet).as("maler med layout :: hjelp('…')").isGreaterThanOrEqualTo(10);
        assertThat(feil).as("hjelpelenker til artikler som ikke finnes").isEmpty();
    }
}
