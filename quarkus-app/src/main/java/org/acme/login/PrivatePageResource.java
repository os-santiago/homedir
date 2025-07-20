package org.acme.login;

import java.io.InputStream;
import java.net.URI;

import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Legacy page backed by a cookie based login. It now responds on "/legacy"
 * so that the application home page can be provided by EventFlow.
 */
@Path("/legacy")
public class PrivatePageResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response get(@CookieParam("user") String user) {
        if (user == null || user.isEmpty()) {
            return Response.seeOther(URI.create("/login.html")).build();
        }
        InputStream page = getClass().getClassLoader()
                .getResourceAsStream("META-INF/resources/private.html");
        if (page == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(page).build();
    }
}

