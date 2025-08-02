package com.scanales.eventflow.private_;

import com.scanales.eventflow.service.EventLoaderService;
import com.scanales.eventflow.service.GitEventSyncService;
import com.scanales.eventflow.service.GitLoadStatus;
import com.scanales.eventflow.service.GitTroubleshootResult;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/api")
public class GitStatusResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    EventLoaderService loader;

    @Inject
    GitEventSyncService gitSync;

    private boolean isAdmin() {
        return AdminUtils.isAdmin(identity);
    }

    @GET
    @Path("/git-status")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response status() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(gitSync.getStatus()).build();
    }

    @POST
    @Path("/git-reload")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response reload() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        gitSync.reloadEventsFromGit();
        GitLoadStatus status = gitSync.getStatus();
        return Response.ok(status).build();
    }

    @GET
    @Path("/git-troubleshoot")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response troubleshoot() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        GitTroubleshootResult result = loader.troubleshoot();
        return Response.ok(result).build();
    }
}
