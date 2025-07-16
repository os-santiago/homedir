package org.acme.login;

import java.io.IOException;
import java.net.URI;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
public class AuthFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        if (path.endsWith("private.html")) {
            Cookie user = ctx.getCookies().get("user");
            if (user == null) {
                ctx.abortWith(Response.seeOther(URI.create("/login.html")).build());
            }
        }
    }
}
