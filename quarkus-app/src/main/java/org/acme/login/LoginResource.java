package org.acme.login;

import java.net.URI;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@Path("/auth")
public class LoginResource {

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(@FormParam("username") String username,
                          @FormParam("password") String password) {
        if ("user".equals(username) && "password".equals(password)) {
            NewCookie cookie = new NewCookie("user", username, "/", null, null, NewCookie.DEFAULT_MAX_AGE, false);
            return Response.seeOther(URI.create("/private.html")).cookie(cookie).build();
        }
        return Response.seeOther(URI.create("/login.html?error=1")).build();
    }

    @GET
    @Path("/logout")
    public Response logout() {
        NewCookie cookie = new NewCookie("user", "", "/", null, null, 0, false);
        return Response.seeOther(URI.create("/")).cookie(cookie).build();
    }
}
