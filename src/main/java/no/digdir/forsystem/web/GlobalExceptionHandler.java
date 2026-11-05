package no.digdir.forsystem.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import no.digdir.forsystem.common.Regelbrudd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders business-rule violations as a friendly error page rather than a stack trace, and any other
 * unexpected failure as {@code error.html}. The latter is rendered here rather than left to Spring
 * Boot's {@code /error} forward, which answers htmx requests (no {@code Accept: text/html}) with
 * JSON — the user would see a blank page. Access denials (LESER attempting a write) are handled by
 * Spring Security (403) before reaching here.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Regelbrudd.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String regelbrudd(Regelbrudd e, Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("melding", e.getMessage());
        model.addAttribute("tilbake", tilbake(request));
        if ("true".equals(request.getHeader("HX-Request"))) {
            // A boosted form would push its failed POST into history, so history.back() only reached that
            // duplicate entry and a second click left the page (kickoff: «må trykke to ganger»).
            response.setHeader("HX-Push-Url", "false");
        }
        return "feil";
    }

    /**
     * Where «Tilbake» goes: the page the failed form was on (htmx's {@code HX-Current-URL}, else
     * {@code Referer}), as a local path. Another site's address is never echoed into the page;
     * then the button falls back to {@code history.back()}.
     */
    static String tilbake(HttpServletRequest request) {
        String kilde = request.getHeader("HX-Current-URL");
        if (kilde == null) {
            kilde = request.getHeader("Referer");
        }
        if (kilde == null) {
            return null;
        }
        try {
            URI uri = new URI(kilde);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return null;
            }
            String sti = uri.getRawPath();
            if (sti == null || !sti.startsWith("/") || sti.startsWith("//")) {
                return null;
            }
            return uri.getRawQuery() == null ? sti : sti + "?" + uri.getRawQuery();
        } catch (URISyntaxException ugyldig) {
            return null;
        }
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    String uventet(RuntimeException e, Model model) {
        if (e instanceof ErrorResponse) {
            // Framework exceptions that carry their own status (e.g. ResponseStatusException) keep it.
            throw e;
        }
        LOG.error("Uventet feil", e);
        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("timestamp", new Date());
        return "error";
    }
}
