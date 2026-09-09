package no.digdir.forsystem.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Dashboard / landing page linking to the registries and the audit log. */
@Controller
class HomeController {

    @GetMapping("/")
    String home() {
        return "index";
    }
}
