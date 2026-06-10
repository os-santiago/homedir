package com.scanales.homedir.error;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.security.ForbiddenException;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ThrowableErrorMapper implements ExceptionMapper<Throwable> {

    @Inject
    Engine engine;

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
        String html = template.render();
        return Response.status(statusCode).entity(html).type(MediaType.TEXT_HTML).build();
    }
}
