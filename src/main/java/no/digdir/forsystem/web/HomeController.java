package no.digdir.forsystem.web;

import no.digdir.forsystem.web.security.Rolle;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The start page as a dashboard (specs/015) and the learning-centre placeholder. Navigation lives in the
 * header menu; the start page shows where invoicing stands and the next step.
 */
@Controller
class HomeController {

    private final ForsideService forside;

    HomeController(ForsideService forside) {
        this.forside = forside;
    }

    @GetMapping("/")
    String home(Authentication auth, Model model) {
        model.addAttribute("forside", forside.les(new ForsideService.Bruker(
                Fornavn.av(auth), harRolle(auth, Rolle.FORVALTER), harRolle(auth, Rolle.GODKJENNER))));
        return "index";
    }

    /** Placeholder until the learning centre has its own spec. */
    @GetMapping("/laering")
    String laering() {
        return "laering";
    }

    private static boolean harRolle(Authentication auth, String rolle) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + rolle)::equals);
    }
}
