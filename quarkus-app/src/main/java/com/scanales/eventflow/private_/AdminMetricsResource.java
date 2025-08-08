package com.scanales.eventflow.private_;

import com.scanales.eventflow.service.MetricsService;
import com.scanales.eventflow.service.MetricsService.Metrics;
import com.scanales.eventflow.util.AdminUtils;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Exposes system metrics for the admin dashboard. */
@Path("/private/admin/metrics")
public class AdminMetricsResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    MetricsService metricsService;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response metrics() {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Metrics metrics = metricsService.getMetrics();
        return Response.ok(metrics).build();
    }
}
