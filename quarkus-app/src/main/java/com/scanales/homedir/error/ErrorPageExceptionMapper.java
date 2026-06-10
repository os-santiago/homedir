package com.scanales.homedir.error;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(4500)
public class ErrorPageExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Inject
    Engine engine;

    @Override
    public Response toResponse(WebApplicationException e) {
        int statusCode = e.getResponse().getStatus();
        String templateId = "errors/" + statusCode;
        Template template = engine.getTemplate(templateId);
        if (template == null) {
            return Response.status(statusCode).entity("Error " + statusCode).type(MediaType.TEXT_PLAIN).build();
        }
        String html;
        try {
            html = template.render();
        } catch (Exception ex) {
            return Response.status(statusCode).entity("Error " + statusCode).type(MediaType.TEXT_PLAIN).build();
        }
        return Response.status(statusCode).entity(html).type(MediaType.TEXT_HTML).build();
    }
}
