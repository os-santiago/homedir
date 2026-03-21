package com.scanales.homedir.private_;

import com.scanales.homedir.service.CapacityService;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.UserScheduleService;
import com.scanales.homedir.util.AdminUtils;
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
    static native TemplateInstance index(
        CapacityService.Status status,
        UserScheduleService.ReadMetrics reads,
        PersistenceService.QueueStats writes);
  }

  @Inject SecurityIdentity identity;

  @Inject CapacityService capacity;

  @Inject UserScheduleService schedules;

  @Inject PersistenceService persistence;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response show() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    CapacityService.Status status = capacity.evaluate();
    UserScheduleService.ReadMetrics reads = schedules.getReadMetrics();
    PersistenceService.QueueStats writes = persistence.getQueueStats();
    return Response.ok(Templates.index(status, reads, writes)).build();
  }
}
