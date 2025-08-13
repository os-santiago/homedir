package com.scanales.eventflow.private_;

import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UsageMetricsService.Summary;
import com.scanales.eventflow.util.AdminUtils;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Simple admin page for usage metrics. */
@Path("/private/admin/metrics")
public class AdminMetricsResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index(long totalKeys, long estimatedSize, long discarded);
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    UsageMetricsService metrics;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response metrics() {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Summary s = metrics.getSummary();
        return Response.ok(Templates.index(s.totalKeys(), s.estimatedSize(), s.discardedEvents())).build();
    }
}
