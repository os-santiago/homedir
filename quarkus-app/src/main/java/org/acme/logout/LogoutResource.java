package org.acme.logout;

import java.net.URI;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * Simple endpoint to clear the q_session cookie and redirect the user
 * to the home page.
 */
@Path("/logout")
public class LogoutResource {

    @GET
    public Response logout() {
        return Response.seeOther(URI.create("/") )
                .header("Set-Cookie", "q_session=; Path=/; Max-Age=0; HttpOnly; Secure")
                .build();
    }
}
