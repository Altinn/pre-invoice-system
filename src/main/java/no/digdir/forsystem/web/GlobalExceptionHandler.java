package no.digdir.forsystem.web;

import no.digdir.forsystem.common.Regelbrudd;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders business-rule violations as a friendly error page rather than a stack trace. Access
 * denials (LESER attempting a write) are handled by Spring Security (403) before reaching here.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Regelbrudd.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String regelbrudd(Regelbrudd e, Model model) {
        model.addAttribute("melding", e.getMessage());
        return "feil";
    }
}
