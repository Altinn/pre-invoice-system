package no.digdir.forsystem.web;

import java.util.Date;

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
    String regelbrudd(Regelbrudd e, Model model) {
        model.addAttribute("melding", e.getMessage());
        return "feil";
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
