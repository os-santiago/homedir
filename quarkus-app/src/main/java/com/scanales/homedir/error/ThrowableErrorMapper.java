package com.scanales.homedir.error;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.ForbiddenException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

@Provider
@Priority(4990)
public class ThrowableErrorMapper implements ExceptionMapper<Throwable> {

    @Inject
    Engine engine;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(Throwable t) {
        int statusCode = 500;
        if (t instanceof WebApplicationException wae) {
            statusCode = wae.getResponse().getStatus();
        } else if (t instanceof ForbiddenException) {
            statusCode = 403;
        }
        Template template = engine.getTemplate("errors/" + statusCode);
        if (template == null) {
            return Response.status(statusCode).entity("Error " + statusCode).type(MediaType.TEXT_PLAIN).build();
        }
        Locale locale = resolveLocale();
        String html;
        try {
            TemplateInstance instance = template.instance();
            if (locale != null) {
                instance.setAttribute(TemplateInstance.LOCALE, locale);
            }
            html = instance.render();
        } catch (Exception ex) {
            return Response.status(statusCode).entity("Error " + statusCode).type(MediaType.TEXT_PLAIN).build();
        }
        return Response.status(statusCode).entity(html).type(MediaType.TEXT_HTML).build();
    }

    private Locale resolveLocale() {
        if (headers == null || headers.getAcceptableLanguages() == null
            || headers.getAcceptableLanguages().isEmpty()) {
            return null;
        }
        try {
            List<LanguageRange> ranges = LanguageRange.parse(
                headers.getAcceptableLanguages().stream()
                    .map(Locale::toLanguageTag).reduce((a, b) -> a + "," + b).orElse("en"));
            Locale best = Locale.lookup(ranges, List.of(Locale.ENGLISH, Locale.of("es")));
            return best != null ? best : Locale.ENGLISH;
        } catch (Exception ex) {
            return null;
        }
    }
}
