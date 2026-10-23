package no.digdir.forsystem.web.security;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

/**
 * Renders a 403 as {@code error.html} saying why, instead of a bare "teknisk feil (403)". Two causes
 * look the same to the user and need different advice: a stale CSRF token (the session ended or the
 * app was redeployed while the form was open — reload and retry) and a missing role. Rendered here
 * rather than via {@code sendError}, because the container's error forward answers htmx requests
 * with JSON (see GlobalExceptionHandler).
 */
@Component
class TilgangNektetHandler implements AccessDeniedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TilgangNektetHandler.class);

    private final ThymeleafViewResolver views;

    TilgangNektetHandler(ThymeleafViewResolver views) {
        this.views = views;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        boolean oktUtlopt = e instanceof CsrfException;
        String method = sanitizeForLog(request.getMethod());
        String requestUri = sanitizeForLog(request.getRequestURI());
        LOG.info("403 {} {}: {}", method, requestUri,
                oktUtlopt ? "utløpt eller manglende CSRF-token" : "mangler rolle");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        try {
            View view = views.resolveViewName("error", request.getLocale());
            view.render(Map.of(
                    "status", HttpStatus.FORBIDDEN.value(),
                    "timestamp", new Date(),
                    "oktUtlopt", oktUtlopt,
                    "tilbakeTil", tilbakeTil(request)), request, response);
        } catch (Exception renderFeil) {
            LOG.warn("Kunne ikke vise feilsiden for 403", renderFeil);
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.FORBIDDEN.value());
            }
        }
    }

    /** The page the form was on, so "reload" fetches a fresh token there. Same-origin path only. */
    static String tilbakeTil(HttpServletRequest request) {
        String hjem = request.getContextPath() + "/";
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return hjem;
        }
        try {
            URI uri = URI.create(referer);
            String sti = uri.getRawPath();
            if (sti == null || !sti.startsWith("/") || sti.startsWith("//") || sti.contains("\\")) {
                return hjem;
            }
            return uri.getRawQuery() == null ? sti : sti + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException ugyldig) {
            return hjem;
        }
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace('\r', '_')
                .replace('\n', '_');
    }
}
