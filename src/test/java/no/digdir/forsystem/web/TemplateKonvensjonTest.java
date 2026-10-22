package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every page template follows the UI contract (specs/004 contracts/ui.md): Norwegian Bokmål, the
 * shared head and header fragments, a skip-link target, design tokens instead of inline styles, the
 * OS colour scheme, and no assets from the internet (Constitution V).
 */
class TemplateKonvensjonTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern INLINE_STYLE = Pattern.compile("\\s(th:)?style=");
    private static final Pattern EXTERNAL_URL = Pattern.compile("(href|src)=\"https?://");

    static List<Path> sider() throws IOException {
        try (Stream<Path> filer = Files.walk(TEMPLATES)) {
            return filer.filter(p -> p.toString().endsWith(".html"))
                    // Fragment-only files (shared layout, kjoring/fragmenter) are not pages.
                    .filter(p -> !p.getFileName().toString().equals("layout.html"))
                    .filter(p -> !p.getFileName().toString().equals("fragmenter.html"))
                    .sorted()
                    .toList();
        }
    }

    @ParameterizedTest
    @MethodSource("sider")
    void sidenFolgerKontrakten(Path side) throws IOException {
        String html = Files.readString(side);
        assertThat(html).as("%s: lang", side).contains("<html lang=\"nb\"");
        assertThat(html).as("%s: shared head", side).contains("layout :: head(");
        assertThat(html).as("%s: shared header with section", side).contains("layout :: topbar(");
        assertThat(html).as("%s: skip-link target", side).contains("id=\"hovedinnhold\"");
        assertThat(html).as("%s: follows the OS colour scheme", side).contains("data-color-scheme=\"auto\"");
        assertThat(INLINE_STYLE.matcher(html).find()).as("%s: inline style attribute", side).isFalse();
        assertThat(EXTERNAL_URL.matcher(html).find()).as("%s: external URL", side).isFalse();
    }

    @ParameterizedTest
    @MethodSource("sider")
    void ingenTilbakepiler(Path side) throws IOException {
        assertThat(Files.readString(side)).as("%s: use breadcrumbs, not \"← X\" links", side).doesNotContain("←");
    }
}
