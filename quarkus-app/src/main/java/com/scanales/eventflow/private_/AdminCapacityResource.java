package com.scanales.eventflow.private_;

import com.scanales.eventflow.service.CapacityService;
import com.scanales.eventflow.service.UserScheduleService;
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

/** Admin panel for capacity status. */
@Path("/private/admin/capacity")
public class AdminCapacityResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index(CapacityService.Status status,
                UserScheduleService.ReadMetrics reads);
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    CapacityService capacity;

    @Inject
    UserScheduleService schedules;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response show() {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        CapacityService.Status status = capacity.evaluate();
        UserScheduleService.ReadMetrics reads = schedules.getReadMetrics();
        return Response.ok(Templates.index(status, reads)).build();
    }
}

