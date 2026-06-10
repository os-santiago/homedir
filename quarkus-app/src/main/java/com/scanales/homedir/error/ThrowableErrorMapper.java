package com.scanales.homedir.error;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
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
        Template template = engine.getTemplate("errors/500");
        if (template == null) {
            return Response.status(500).entity("Internal Server Error").type(MediaType.TEXT_PLAIN).build();
        }
        String html = template.render();
        return Response.status(500).entity(html).type(MediaType.TEXT_HTML).build();
    }
}
