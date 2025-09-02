package io.eventflow.notifications.global;

import io.eventflow.time.AppClock;
import io.eventflow.time.SimulatedClock;
import com.scanales.eventflow.service.EventService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** REST resource to simulate notification generation. */
@Path("/admin/api/notifications/sim")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class NotificationSimulationResource {

  @Inject SimulatedClock simClock;
  @Inject AppClock appClock;
  @Inject EventService events;
  @Inject GlobalNotificationService service;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @ConfigProperty(name = "notifications.upcoming.window", defaultValue = "PT5M")
  Duration upcomingWin;

  @ConfigProperty(name = "notifications.endingSoon.window", defaultValue = "PT5M")
  Duration endingWin;

  @ConfigProperty(name = "notifications.simulation.allow-real", defaultValue = "false")
  boolean allowReal;

  @ConfigProperty(name = "notifications.simulation.max-items", defaultValue = "500")
  int maxItems;

  /** Request DTO. */
  public static class SimRequest {
    public String eventId;
    public String mode; // preview | test-broadcast | real-broadcast
    public Instant pivot;
    public boolean includeEvent = true;
    public boolean includeTalks = true;
    public boolean includeBreaks = true;
    public List<String> states;
    public boolean sequence;
    public long paceMs = 1500;
  }

  @POST
  @Path("/dry-run")
  public Response dryRun(SimRequest req) {
    Instant pivot = Optional.ofNullable(req.pivot).orElse(appClock.now());
    req.pivot = pivot;
    simClock.set(pivot);
    try {
      List<GlobalNotification> plan =
          SimulationEngine.plan(req, events, upcomingWin, endingWin);
      if (plan.size() > maxItems) {
        plan = plan.subList(0, maxItems);
      }
      return Response.ok(plan).build();
    } finally {
      simClock.clear();
    }
  }

  @POST
  @Path("/execute")
  public Response execute(SimRequest req) {
    Instant pivot = Optional.ofNullable(req.pivot).orElse(appClock.now());
    req.pivot = pivot;
    simClock.set(pivot);
    try {
      List<GlobalNotification> plan =
          SimulationEngine.plan(req, events, upcomingWin, endingWin);
      if (plan.size() > maxItems) {
        plan = plan.subList(0, maxItems);
      }
      if ("preview".equals(req.mode) || req.mode == null) {
        return Response.ok(plan).build();
      }
      if ("real-broadcast".equals(req.mode) && !allowReal) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "real-broadcast disabled"))
            .build();
      }
      boolean testFlag = !"real-broadcast".equals(req.mode);
      if (req.sequence) {
        final Instant p = pivot;
        for (int i = 0; i < plan.size(); i++) {
          GlobalNotification n = plan.get(i);
          long delay = Math.max(0, req.paceMs) * i;
          scheduler.schedule(
              () -> {
                simClock.set(p);
                try {
                  n.test = testFlag;
                  n.createdAt = p.toEpochMilli();
                  service.enqueue(n);
                } finally {
                  simClock.clear();
                }
              },
              delay,
              TimeUnit.MILLISECONDS);
        }
        return Response.accepted(Map.of("scheduled", plan.size(), "test", testFlag)).build();
      }
      for (GlobalNotification n : plan) {
        n.test = testFlag;
        n.createdAt = pivot.toEpochMilli();
        service.enqueue(n);
      }
      return Response.ok(Map.of("enqueued", plan.size(), "test", testFlag)).build();
    } finally {
      simClock.clear();
    }
  }
}
