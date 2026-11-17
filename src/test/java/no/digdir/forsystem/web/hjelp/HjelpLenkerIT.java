package no.digdir.forsystem.web.hjelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import no.digdir.forsystem.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * Every link from the learning center into the app must hit a page the app really serves (specs/016 FR-016,
 * research R4). This is the drift that removing a menu entry would otherwise cause silently.
 */
class HjelpLenkerIT extends IntegrationTest {

    private static final Pattern TALL_I_STI = Pattern.compile("(^|/)\\d+(/|$)");

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping;
    @Autowired Hjelpebibliotek bibliotek;

    @Test
    void hverLenkeTilAppenTreffer_enGetRute() {
        List<PathPattern> getRuter = new ArrayList<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            var metoder = info.getMethodsCondition().getMethods();
            if ((metoder.isEmpty() || metoder.contains(RequestMethod.GET)) && info.getPathPatternsCondition() != null) {
                getRuter.addAll(info.getPathPatternsCondition().getPatterns());
            }
        }

        List<String> feil = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : bibliotek.appLenker().entrySet()) {
            for (String sti : e.getValue()) {
                if (TALL_I_STI.matcher(sti).find()) {
                    feil.add(e.getKey() + " → " + sti + "  (lenk til sider, ikke til data: ingen id i stien)");
                } else if (getRuter.stream().noneMatch(p -> p.matches(PathContainer.parsePath(sti)))) {
                    feil.add(e.getKey() + " → " + sti + "  (appen har ingen GET-side her)");
                }
            }
        }
        assertThat(feil).as("lenker fra Læringssenteret til sider appen ikke serverer").isEmpty();
        assertThat(bibliotek.appLenker()).as("det finnes lenker å sjekke").isNotEmpty();
    }
}
