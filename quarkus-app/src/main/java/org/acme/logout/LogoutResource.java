package org.acme.logout;

import java.net.URI;

import org.jboss.logging.Logger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * Simple endpoint to clear the q_session cookie and redirect the user
 * to the home page.
 */
@Path("/logout")
public class LogoutResource {

    private static final Logger LOG = Logger.getLogger(LogoutResource.class);

    @GET
    public Response logout() {
        LOG.info("Processing logout request");
        return Response.seeOther(URI.create("/") )
                .header("Set-Cookie", "q_session=; Path=/; Max-Age=0; HttpOnly; Secure")
                .build();
    }
}
